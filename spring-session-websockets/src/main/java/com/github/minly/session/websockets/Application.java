/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.github.minly.session.websockets;

import static org.springframework.messaging.simp.SimpMessageType.MESSAGE;
import static org.springframework.messaging.simp.SimpMessageType.SUBSCRIBE;

import org.h2.server.web.WebServlet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry;
import org.springframework.security.config.annotation.web.socket.AbstractSecurityWebSocketMessageBrokerConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.data.repository.query.SecurityEvaluationContextExtension;
import org.springframework.session.ExpiringSession;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.socket.config.annotation.AbstractSessionWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

import com.github.minly.session.websockets.data.ActiveWebSocketUserRepository;
import com.github.minly.session.websockets.websocket.WebSocketConnectHandler;
import com.github.minly.session.websockets.websocket.WebSocketDisconnectHandler;

/**
 * @author Rob Winch
 */
@SpringBootApplication
public class Application {

	@Configuration
	static class H2Config {

		@Bean
		public ServletRegistrationBean h2Servlet() {
			ServletRegistrationBean servletBean = new ServletRegistrationBean();
			servletBean.addUrlMappings("/h2/*");
			servletBean.setServlet(new WebServlet());
			return servletBean;
		}
	}

	@Configuration
	@EnableWebSecurity
	@EnableGlobalMethodSecurity(prePostEnabled = true)
	@EnableRedisHttpSession
	static class WebSecurityConfig extends WebSecurityConfigurerAdapter {
		@Override
		protected void configure(HttpSecurity http) throws Exception {

			http
					.csrf()
					.ignoringAntMatchers("/chat/**")
					.and()
					.headers()
					.frameOptions().sameOrigin()
					.and()
					.authorizeRequests()
					.anyRequest().authenticated()
					.and()
					.formLogin()
					.and()
					.logout()
					.permitAll();
		}

		@Autowired
		public void configureGlobal(AuthenticationManagerBuilder auth, UserDetailsService userDetailsService) throws Exception {
			auth
					.userDetailsService(userDetailsService)
					.passwordEncoder(new BCryptPasswordEncoder());
		}

		@Bean
		public SecurityEvaluationContextExtension securityEvaluationContextExtension() {
			return new SecurityEvaluationContextExtension();
		}
	}

	@Configuration
	static class WebSocketHandlersConfig<S extends ExpiringSession> {

		@Bean
		public WebSocketConnectHandler<S> webSocketConnectHandler(SimpMessageSendingOperations messagingTemplate,
		                                                          ActiveWebSocketUserRepository repository) {
			return new WebSocketConnectHandler<S>(messagingTemplate, repository);
		}

		@Bean
		public WebSocketDisconnectHandler<S> webSocketDisconnectHandler(SimpMessageSendingOperations messagingTemplate,
		                                                                ActiveWebSocketUserRepository repository) {
			return new WebSocketDisconnectHandler<S>(messagingTemplate, repository);
		}
	}

	@Configuration
	@EnableScheduling
	@EnableWebSocketMessageBroker
	static class WebSocketConfig
			extends AbstractSessionWebSocketMessageBrokerConfigurer<ExpiringSession> {
			// registers SessionRepositoryMessageInterceptor which updates HTTP session

		protected void configureStompEndpoints(StompEndpointRegistry registry) {
			registry.addEndpoint("/messages").withSockJS();
		}

		public void configureMessageBroker(MessageBrokerRegistry registry) {
			registry.enableSimpleBroker("/queue/", "/topic/");
			registry.setApplicationDestinationPrefixes("/app");
		}
	}

	@Configuration
	static class WebSocketSecurityConfig extends AbstractSecurityWebSocketMessageBrokerConfigurer {

		@Override
		protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
			messages
					// message types other than MESSAGE and SUBSCRIBE
					.nullDestMatcher().authenticated()
					// anyone can access the errors
					.simpDestMatchers("/user/queue/errors").permitAll()
					// matches any destination that starts with /app/
					.simpDestMatchers("/app/**").authenticated()
					// matches any destination for SimpMessageType.SUBSCRIBE that starts with /user/ or /topic/friends/
					.simpSubscribeDestMatchers("/user/**", "/topic/friends/*").authenticated()

					// (i.e. cannot send messages directly to /topic/, /queue/)
					// (i.e. cannot subscribe to /topic/messages/* to get messages sent to /topic/messages-user<id>)
					.simpTypeMatchers(MESSAGE, SUBSCRIBE).denyAll()
					// catch all
					.anyMessage().denyAll();
		}
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
