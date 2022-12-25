package com.fd.web.controller;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fd.microSevice.helper.ApiInfo;
import com.fd.microSevice.helper.ClientInfo;
import com.fd.microSevice.helper.CoordinateUtil;
import com.fd.web.vo.ApiInfoVo;

@Controller
@RequestMapping("/home")
public class HomeController {
	static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 * 移除客户端
	 * 
	 * @param req
	 * @return
	 */
	@RequestMapping("/removeClient")
	public String removeClient(HttpServletRequest req) {
		String sid = req.getParameter("sid");
		log.info("sid:{}", sid);
		Iterator<ClientInfo> ite = CoordinateUtil.CLIENTS.iterator();
		while (ite.hasNext()) {
			ClientInfo c = ite.next();
			try {
				log.info(c.getSession().getId());

				if (c.getSession().getId().equals(sid)) {
					if (c.getSession().isOpen()) {
						c.getSession().close();
						log.info("remove success");
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("removeClient", e);
			}
		}
		return "redirect:/home/clientlist";
	}

	/**
	 * 客户端列表
	 * 
	 * @param req
	 * @return
	 */
	@RequestMapping("/clientlist")
	public String clientlist(HttpServletRequest req) {
		List<ApiInfoVo> aivs = new ArrayList<>();
		for (ClientInfo c : CoordinateUtil.CLIENTS) {
			if (c.getSession().isOpen()) {
				int count = Long.valueOf(c.getClientApi().getApis().stream().map(ApiInfo::getName).distinct().count())
						.intValue();
				ApiInfoVo av = new ApiInfoVo(c.getClientApi().getHttpApiInfo().getHost(),
						c.getClientApi().getHttpApiInfo().getPort(), count, "",
						c.getClientApi().getHttpApiInfo().getContextPath());
				av.setSid(c.getSession().getId());
				aivs.add(av);
			}
		}
		req.setAttribute("cas", aivs);
		return "client";
	}

	/**
	 * 主机列表
	 * 
	 * @return
	 */
	@RequestMapping("/hostlist")
	public String hostlist(HttpServletRequest req, String sname) {
		List<ApiInfoVo> aivs = new ArrayList<>();
		for (ClientInfo c : CoordinateUtil.CLIENTS) {
			if (c.getSession().isOpen()) {
				int count = Long.valueOf(c.getClientApi().getApis().stream().map(ApiInfo::getName).distinct().count())
						.intValue();
				if (c.getClientApi().getApis().size() > 0) {
					a: for (ApiInfo api : c.getClientApi().getApis()) {
						ApiInfoVo av = new ApiInfoVo(c.getClientApi().getHttpApiInfo().getHost(),
								c.getClientApi().getHttpApiInfo().getPort(), count, api.getName(),
								c.getClientApi().getHttpApiInfo().getContextPath());
						for (ApiInfoVo a : aivs) {
							if (a.equals(av)) {
								continue a;
							}
						}
						aivs.add(av);
					}
				} else {
					ApiInfoVo av = new ApiInfoVo(c.getClientApi().getHttpApiInfo().getHost(),
							c.getClientApi().getHttpApiInfo().getPort(), count, "",
							c.getClientApi().getHttpApiInfo().getContextPath());
					aivs.add(av);
				}
			}
		}
		if (sname != null && sname.trim().length() > 1) {
			List<ApiInfoVo> collect = aivs.stream().filter(a -> a.getName().contains(sname.trim()))
					.collect(Collectors.toList());
			log.info("name:{},cosize:{}", sname, collect.size());
			req.setAttribute("cas", collect);
			req.setAttribute("sname", sname);
		} else {
			req.setAttribute("cas", aivs);
		}
		return "host";
	}

}
