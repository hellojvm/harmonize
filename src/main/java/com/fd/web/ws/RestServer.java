package com.fd.web.ws;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fd.microSevice.code.RestCode;
import com.fd.microSevice.helper.ApiInfo;
import com.fd.microSevice.helper.ClientApi;
import com.fd.microSevice.helper.ClientInfo;
import com.fd.microSevice.helper.CoordinateUtil;
import com.fd.microSevice.helper.HttpApiInfo;
import com.fd.microSevice.helper.ReqInfo;
import com.fd.web.listener.WsServerListener;

/**
 * 分布式协调服务
 * 
 * @author 符冬
 *
 */
@Component
@ServerEndpoint(value = "/restcoordinate", configurator = RestServerConfigurator.class, decoders = {
		RestCode.class }, encoders = { RestCode.class })
public class RestServer {
	private static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private ReqInfo reqInfo;

	@OnOpen
	public void open(Session session, EndpointConfig config) {
		int maxbufsize = session.getMaxBinaryMessageBufferSize() * 90;
		session.setMaxBinaryMessageBufferSize(maxbufsize);
		session.setMaxTextMessageBufferSize(maxbufsize);
		this.reqInfo = (ReqInfo) config.getUserProperties().get(WsServerListener.REQ_INFO);
		sendapis(session);
	}

	private void sendapis(Session session) {
		CoordinateUtil.STE.submit(() -> {
			for (ClientInfo ci : CoordinateUtil.CLIENTS) {
				if (!ci.getSession().getId().equals(session.getId()) && session.isOpen()) {
					try {
						session.getBasicRemote().sendObject(ci.getClientApi());
					} catch (Exception e) {
						e.printStackTrace();
						log.error("open", e);
					}
				}
			}

		});
	}

	@OnError
	public void error(Throwable e, Session session) {
		log.error("error", e);
		CoordinateUtil.STE.submit(() -> {
			try {
				if (session.isOpen()) {
					session.close();
				}
				HttpApiInfo ha = CoordinateUtil.getHttpApiInfo(session);
				if (ha != null) {
					log.error(String.format("客户端%s出错", ha.getBaseUrl()), e);
				}
			} catch (Exception e1) {
				e1.printStackTrace();
				log.error("严重了。。。。。。。。。。。。。。", e1);
			}

		});
	}

	@OnClose
	public void close(Session session, CloseReason cr) {
		log.info("{},{},open={}", cr.getCloseCode(), cr.getReasonPhrase(), session.isOpen());
		CoordinateUtil.STE.submit(() -> {
			HttpApiInfo ha = CoordinateUtil.getHttpApiInfo(session);
			if (ha != null) {
				log.error(String.format("客户端%s关闭连接..", ha.getBaseUrl()));
				if (!cr.getCloseCode().equals(CloseCodes.UNEXPECTED_CONDITION)
						&& !cr.getCloseCode().equals(CloseCodes.GOING_AWAY)) {
					Iterator<ClientInfo> ite = CoordinateUtil.CLIENTS.iterator();
					while (ite.hasNext()) {
						ClientInfo disapicl = ite.next();
						if (disapicl.getSession().getId().equals(session.getId())) {
							ite.remove();
							ClientApi clientApi = disapicl.getClientApi();
							clientApi.getHttpApiInfo().setIsOnline(false);
							sendapi(clientApi, session);
							log.error(String.format("%s客户端销毁成功..", session.getId()));
							log.error("还剩客户端总数量:{}", CoordinateUtil.CLIENTS.size());
						}
					}
				}
			} else {
				log.error("CoordinateUtil.CLIENTS:" + CoordinateUtil.CLIENTS.size());
			}

		});
	}

	@OnMessage
	public void handlerData(ClientApi api, Session session) {
		CoordinateUtil.STE.submit(() -> {

			try {
				if (api.getSync()) {
					for (ApiInfo ai : api.getApis()) {
						ApiInfo apiInfo = CoordinateUtil.getApiInfo(ai.getName(), ai.getMethod());
						Set<ClientApi> cas = CoordinateUtil.getCasByApiInfo(apiInfo,
								CoordinateUtil.CLIENTS.stream().map(o -> o.getClientApi()).collect(Collectors.toSet()));
						for (ClientApi ca : cas) {
							if (ca != null && session.isOpen()) {
								session.getBasicRemote().sendObject(ca);
								log.info("主动同步成功。。。。:{}", ca);
							}
						}
					}

				} else if (api.getHttpApiInfo() != null) {
					if (api.getHttpApiInfo().getContextPath() != null) {
						log.info(String.format("新增前，客户端总数量：%s", CoordinateUtil.CLIENTS.size()));
						if (api.getHttpApiInfo().getHost() == null
								|| api.getHttpApiInfo().getHost().trim().length() < 4) {
							api.getHttpApiInfo().setHost(reqInfo.getRemoteAddr());
						}
						if (api.getHttpApiInfo().getIsOnline()) {
							ClientInfo curClient = new ClientInfo(api, session);
							CoordinateUtil.CLIENTS.add(curClient);
							sendapis(session);
							log.info(String.format("服务器%s上线", api.getHttpApiInfo().getBaseUrl()));
						} else {
							log.info(String.format("服务器%s下线", api.getHttpApiInfo().getBaseUrl()));
							CoordinateUtil.CLIENTS.stream().filter(c -> c.getClientApi().equals(api)).forEach(c -> {
								if (c.getSession().isOpen()) {
									try {
										c.getSession().close();
									} catch (IOException e) {
										e.printStackTrace();
										log.error("", e);
									}
								}
							});
							CoordinateUtil.CLIENTS.remove(new ClientInfo(api));
						}
						sendapi(api, session);
						log.info(String.format("添加完毕，当前客户端总数量：%s", CoordinateUtil.CLIENTS.size()));
					} else {
						log.error(String.format("%s ContextPath is  null  ", api.getHttpApiInfo().getHost()));
					}
				} else {
					log.error("非法请求...............................getHttpApiInfo is null .........");
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("handlerData", e);
			}

		});
	}

	private void sendapi(ClientApi api, Session session) {
		CoordinateUtil.CLIENTS.stream().forEach(ci -> {
			Session se = ci.getSession();
			if (!se.getId().equals(session.getId()) && se.isOpen()) {
				try {
					HttpApiInfo ha = CoordinateUtil.getHttpApiInfo(se);
					if (ha != null) {
						log.info(String.format("发送给%s", ha.getBaseUrl()));
						se.getBasicRemote().sendObject(api);
					}
				} catch (Exception e) {
					log.error(String.format("出现 错误%s", se.getId()), e);
					e.printStackTrace();
				}
			}

		});

	}

	private final static ScheduledExecutorService SESPOOL = Executors.newScheduledThreadPool(3);
	static {
		SESPOOL.scheduleWithFixedDelay(() -> {
			try {
				log.info("CLIENTS:{}", CoordinateUtil.CLIENTS.size());
				for (ClientInfo client : CoordinateUtil.CLIENTS) {
					if (client.getSession().isOpen()) {
						client.getSession().getBasicRemote().sendPing(ByteBuffer.wrap("".getBytes()));
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("sendping:", e);
			}
		}, 13, 9, TimeUnit.SECONDS);

	}

}
