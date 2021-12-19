package com.fd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import com.fd.web.listener.WsServerListener;

@ServletComponentScan(basePackageClasses = { WsServerListener.class })
@SpringBootApplication
public class MyregistrationApplication {

	public static void main(String[] args) {
		SpringApplication.run(MyregistrationApplication.class, args);
	}

	@Bean
	public ServerEndpointExporter serverEndpointExporter() {
		return new ServerEndpointExporter();
	}
}
