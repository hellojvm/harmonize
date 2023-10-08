package com.fd.web.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fd.microSevice.helper.ClientApi;
import com.fd.microSevice.helper.ClientInfo;
import com.fd.microSevice.helper.CoordinateUtil;

@RestController
public class ApiController {
	/**
	 * 主动获取所有API信息
	 * 
	 */
	@RequestMapping(CoordinateUtil.FETCH_API)
	public List<ClientApi> fetchApis() {
		return CoordinateUtil.CLIENTS.stream().map(ClientInfo::getClientApi).collect(Collectors.toList());

	}
}
