package com.fd.web.ws;

import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerEndpointConfig.Configurator;

import com.fd.web.listener.WsServerListener;

/**
 * 获取HTTP请求客户端数据
 * 
 * @author 符冬
 *
 */
public class RestServerConfigurator extends Configurator {
	@Override
	public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
		HttpSession session = (HttpSession) request.getHttpSession();
		sec.getUserProperties().put(WsServerListener.REQ_INFO, session.getAttribute(WsServerListener.REQ_INFO));
	}

}
