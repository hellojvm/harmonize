package com.fd.web.ws;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
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
import com.fd.microSevice.helper.MyJsonUtils;
import com.fd.microSevice.helper.ReqInfo;
import com.fd.web.listener.WsServerListener;

/**
 * 分布式协调服务
 * 
 * @author 符冬
 *
 */
@Component
@ServerEndpoint(value = CoordinateUtil.SERVER_API, configurator = RestServerConfigurator.class, decoders = {
		RestCode.class }, encoders = { RestCode.class })
public class RestServer {
	private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private ReqInfo reqInfo;

	@OnOpen
	public void open(Session session, EndpointConfig config) {
		int maxbufsize = session.getMaxBinaryMessageBufferSize() * 300;
		session.setMaxBinaryMessageBufferSize(maxbufsize);
		session.setMaxTextMessageBufferSize(maxbufsize);
		this.reqInfo = (ReqInfo) config.getUserProperties().get(WsServerListener.REQ_INFO);
		log.info("open:{}", reqInfo.getRemoteAddr());
		sendapis(session);
	}

	private void sendapis(Session session) {
		CoordinateUtil.STE.submit(() -> {
			for (ClientInfo ci : CoordinateUtil.CLIENTS) {
				if (!ci.getSession().getId().equals(session.getId()) && session.isOpen()) {
					try {
						session.getBasicRemote().sendObject(ci.getClientApi());
					} catch (Exception e) {
						log.error("sendapis", e);
					}
				}
			}

		});
	}

	@OnError
	public void error(Throwable e, Session session) {
		log.error("error:{}", reqInfo.getRemoteAddr(), e);
		CoordinateUtil.STE.submit(() -> {
			try {
				if (session.isOpen()) {
					session.close();
				}
				HttpApiInfo ha = CoordinateUtil.getHttpApiInfo(session);
				if (ha != null) {
					log.error("客户端{}出错", ha.getBaseUrl());
				}
			} catch (Exception e1) {
				log.error("严重了。。。。。。。。。。。。。。", e1);
			}

		});
	}

	@OnClose
	public void close(Session session, CloseReason cr) {
		log.info("{}:{},{},open={}", reqInfo.getRemoteAddr(), cr.getCloseCode(), cr.getReasonPhrase(),
				session.isOpen());
		CoordinateUtil.STE.submit(() -> {
			HttpApiInfo ha = CoordinateUtil.getHttpApiInfo(session);
			if (ha != null) {
				log.error(String.format("客户端%s关闭连接..", ha.getBaseUrl()));
			} else {
				log.error("CoordinateUtil.CLIENTS:" + CoordinateUtil.CLIENTS.size());
			}
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
						log.info("{}:{}客户端销毁成功..", session.getId(), reqInfo.getRemoteAddr());
						log.info("还剩客户端总数量:{}", CoordinateUtil.CLIENTS.size());
					}
				}
			}
		});
	}

	@OnMessage
	public void handlerData(ClientApi api, Session session) {
		CoordinateUtil.STE.submit(() -> {
			try {
				log.info("handlerData:req:: {}", MyJsonUtils.getJsonString(api));
				if (api.getSync()) {
					for (ApiInfo ai : api.getApis()) {
						ApiInfo apiInfo = CoordinateUtil.getApiInfo(ai.getName());
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
						log.info("新增前，客户端总数量：{}", CoordinateUtil.CLIENTS.size());
						if (api.getHttpApiInfo().getHost() == null
								|| api.getHttpApiInfo().getHost().trim().length() < 4) {
							api.getHttpApiInfo().setHost(reqInfo.getRemoteAddr());
						}
						if (api.getHttpApiInfo().getIsOnline()) {
							ClientInfo curClient = new ClientInfo(api, session);
							CoordinateUtil.CLIENTS.add(curClient);
							sendapis(session);
							sendapi(api, session);
							log.info("{} :注册成功。。。。:{}", reqInfo.getRemoteAddr(), api.getHttpApiInfo().getBaseUrl());
						} else {
							try (Socket s = new Socket()) {
								s.connect(new InetSocketAddress(api.getHttpApiInfo().getHost(),
										api.getHttpApiInfo().getPort()), 1000);
								CoordinateUtil.STE.submit(() -> {
									CoordinateUtil.CLIENTS.stream().filter(c -> c.getClientApi().equals(api))
											.forEach(c -> {
												if (c.getSession().isOpen()) {
													try {
														final long timeout = 1
																+ ThreadLocalRandom.current().nextLong(6);
														log.info("熔断：{}秒", timeout);
														TimeUnit.SECONDS.sleep(timeout);
														session.getBasicRemote().sendObject(c.getClientApi());
														log.info("熔断结束");
													} catch (Exception e) {
														log.error("sendapis", e);
													}
												}
											});
								});
								s.close();
							} catch (IOException e2) {
								HttpApiInfo ha = CoordinateUtil.getHttpApiInfo(session);
								if (ha != null) {
									log.warn("{}:服务器{}无法访问{},主动踢下线", reqInfo.getRemoteAddr(), ha.getBaseUrl(),
											api.getHttpApiInfo().getBaseUrl());
								}
								CoordinateUtil.CLIENTS.stream().filter(c -> c.getClientApi().equals(api)).forEach(c -> {
									if (c.getSession().isOpen()) {
										try {
											c.getSession().close();
										} catch (IOException e) {
											log.error("", e);
										}
									}
								});
								CoordinateUtil.CLIENTS.remove(new ClientInfo(api));
								sendapi(api, session);
							}
						}

						log.info("添加完毕，当前客户端总数量：{}", CoordinateUtil.CLIENTS.size());
					} else {
						log.error("{}:{} ContextPath is  null  ", api.getHttpApiInfo().getHost(),
								reqInfo.getRemoteAddr());
					}
				} else {
					log.error("{}:非法请求...............................getHttpApiInfo is null .........",
							reqInfo.getRemoteAddr());
				}
			} catch (Exception e) {
				log.error("handlerData:{}", reqInfo.getRemoteAddr(), e);
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
						log.info("发送给:{}", ha.getBaseUrl());
						se.getBasicRemote().sendObject(api);
					}
				} catch (Exception e) {
					log.error("sendapi 出现 错误{}", reqInfo.getRemoteAddr(), e);
				}
			}

		});

	}

	private final static ScheduledExecutorService SESPOOL = Executors.newScheduledThreadPool(2);
	static {
		SESPOOL.scheduleWithFixedDelay(() -> {
			try {
				log.info("CLIENTS:{}", CoordinateUtil.CLIENTS.size());
				Iterator<ClientInfo> ite = CoordinateUtil.CLIENTS.iterator();
				while (ite.hasNext()) {
					ClientInfo client = ite.next();
					if (client.getSession().isOpen()) {
						client.getSession().getBasicRemote().sendPing(ByteBuffer.wrap("".getBytes()));
					} else {
						ite.remove();
					}
				}
			} catch (Exception e) {
				log.error("sendping:", e);
			}
			System.gc();
		}, 21, 29, TimeUnit.SECONDS);

	}

}
