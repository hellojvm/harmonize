package com.fd.web.listener;

import java.lang.invoke.MethodHandles;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

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

	public final static String[] LOCAL_IPS = { "0:0:0:0:0:0:0:1", "127.0.0.1", "::1", "localhost" };

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

	/**
	 * 得到用户的IP地址
	 * 
	 * @param req
	 * @return
	 */
	public static String getUserIp(HttpServletRequest req) {
		String clientIp = req.getHeader("X-Real-IP");
		if (ObjectUtils.isEmpty(clientIp)) {
			return getrmip(req);
		} else {
			return clientIp;
		}

	}

	private final static Set<String> LANADDRESS = getLanAddress();

	private final static Set<String> getLanAddress() {
		Set<String> las = new HashSet<String>(3);
		try {
			Enumeration<NetworkInterface> ccs = NetworkInterface.getNetworkInterfaces();
			while (ccs.hasMoreElements()) {
				NetworkInterface cc = ccs.nextElement();
				if (!cc.isLoopback() && !cc.isVirtual() && cc.isUp()) {
					Enumeration<InetAddress> sss = cc.getInetAddresses();
					while (sss.hasMoreElements()) {
						InetAddress ia = sss.nextElement();
						if (isIpv4(ia)) {
							las.add(ia.getHostAddress());
						}
					}
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}
		return las;
	}

	private static String getrmip(HttpServletRequest req) {
		String remoteAddr = req.getRemoteAddr();
		if (remoteAddr.equals(LOOPBACKADDRESS)
				|| Arrays.asList(LOCAL_IPS).stream().anyMatch(o -> o.equals(remoteAddr))) {
			return LANADDRESS.iterator().next();
		} else {
			return remoteAddr;
		}
	}
}
