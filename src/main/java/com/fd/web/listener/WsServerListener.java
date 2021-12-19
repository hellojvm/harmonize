package com.fd.web.listener;

import java.lang.invoke.MethodHandles;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import com.fd.microSevice.helper.ReqInfo;

/**
 * 获取HTTP请求客户端数据
 * 
 * @author 符冬
 *
 */
@WebListener
public class WsServerListener implements ServletRequestListener {
	private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	public final static String REQ_INFO = "REQ_INFO";

	@Override
	public void requestDestroyed(ServletRequestEvent sre) {
		HttpServletRequest req = (HttpServletRequest) sre.getServletRequest();
		req.getSession().removeAttribute(REQ_INFO);
	}

	@Override
	public void requestInitialized(ServletRequestEvent sre) {
		HttpServletRequest req = (HttpServletRequest) sre.getServletRequest();
		ReqInfo reqinfo = new ReqInfo(getUserIp(req));
		req.getSession().setAttribute(REQ_INFO, reqinfo);
	}

	public static String LOOPBACKADDRESS = "";
	static {

		try {
			Enumeration<NetworkInterface> ccs = NetworkInterface.getNetworkInterfaces();
			while (ccs.hasMoreElements()) {
				NetworkInterface cc = ccs.nextElement();
				if (cc.isLoopback() && cc.isUp()) {
					Enumeration<InetAddress> sss = cc.getInetAddresses();
					while (sss.hasMoreElements()) {
						InetAddress ia = sss.nextElement();
						if (isIpv4(ia)) {
							LOOPBACKADDRESS = ia.getHostAddress();
						}
					}
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
			log.error("LOOPBACKADDRESS:", e);
		}

	}

	private static boolean isIpv4(InetAddress ia) {
		return ia instanceof Inet4Address;
	}

	public static String LANADDRESS = "";
	static {

		try {
			Enumeration<NetworkInterface> ccs = NetworkInterface.getNetworkInterfaces();
			while (ccs.hasMoreElements()) {
				NetworkInterface cc = ccs.nextElement();
				if (!cc.isLoopback() && !cc.isVirtual() && cc.isUp()) {
					Enumeration<InetAddress> sss = cc.getInetAddresses();
					while (sss.hasMoreElements()) {
						InetAddress ia = sss.nextElement();
						if (isIpv4(ia)) {
							LANADDRESS = ia.getHostAddress();
							log.info("服务注册来源地:{}", ia.getHostAddress());
						}
					}
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
			log.error("LANADDRESS:", e);
		}

	}

	/**
	 * 得到用户的IP地址
	 * 
	 * @param req
	 * @return
	 */
	public static String getUserIp(HttpServletRequest req) {
		String clientIp = req.getHeader("X-Real-IP");
		if (ObjectUtils.isEmpty(clientIp)) {
			clientIp = req.getHeader("X-Forwarded-For");
			if (ObjectUtils.isEmpty(clientIp)) {
				return getrmip(req);
			} else {
				return clientIp;
			}
		} else {
			return clientIp;
		}

	}

	private static String getrmip(HttpServletRequest req) {
		String remoteAddr = req.getRemoteAddr();
		if (remoteAddr.equals(LOOPBACKADDRESS)) {
			return LANADDRESS;
		} else {
			return remoteAddr;
		}
	}
}
