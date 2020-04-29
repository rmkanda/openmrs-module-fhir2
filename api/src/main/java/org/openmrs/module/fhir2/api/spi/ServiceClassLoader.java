/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.fhir2.api.spi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * This is an implementation of the Java Service Provider Interface that differs from
 * {@link ServiceLoader} in that it returns class objects rather than instantiated objects. Of
 * course, this means that it cannot guarantee that the provided services are loaded once and only
 * once.
 *
 * @param <C>
 */
@Slf4j
public class ServiceClassLoader<C> {
	
	private static final String SERVICE_LOCATION = "META-INF/services/";
	
	private static final Pattern COMMENT = Pattern.compile("\\s*#.*$");
	
	private final Class<C> service;
	
	private final ClassLoader classLoader;
	
	private final Set<Class<? extends C>> classes = new HashSet<>();
	
	private volatile boolean loaded = false;
	
	@SuppressWarnings("unused")
	public ServiceClassLoader(Class<C> service) {
		this(service, ClassLoader.getSystemClassLoader());
	}
	
	public ServiceClassLoader(Class<C> service, ClassLoader classLoader) {
		this.service = service;
		this.classLoader = classLoader;
	}
	
	public Set<Class<? extends C>> load() {
		if (!loaded) {
			synchronized (this) {
				if (!loaded) {
					loadResources();
					this.loaded = true;
				}
			}
		}
		
		return new CopyOnWriteArraySet<>(classes);
	}
	
	private void loadResources() {
		final Enumeration<URL> urls;
		try {
			urls = classLoader.getResources(SERVICE_LOCATION + service.getName());
		}
		catch (IOException e) {
			throw new ServiceConfigurationError(service.getName() + ": Error locating configuration files", e);
		}
		
		while (urls.hasMoreElements()) {
			parse(urls.nextElement());
		}
	}
	
	@SuppressWarnings("unchecked")
	private void parse(URL url) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
			String line = reader.readLine();
			while (line != null) {
				String className = parseLine(line);
				
				Class<?> c;
				try {
					c = Class.forName(className, false, classLoader);
				}
				catch (ClassNotFoundException e) {
					throw new ServiceConfigurationError(service.getName() + ": Provider " + className + " not found");
				}
				
				if (!service.isAssignableFrom(c)) {
					throw new ServiceConfigurationError(
					        service.getName() + ": Provider " + className + " is not a subtype of " + service.getName());
				}
				
				// technically unchecked, but type-safe at this point
				classes.add((Class<? extends C>) c);
				
				line = reader.readLine();
			}
		}
		catch (IOException e) {
			throw new ServiceConfigurationError(service.getName() + ": Error reading configuration file");
		}
	}
	
	private String parseLine(String line) {
		// remove comments
		line = COMMENT.split(line, 1)[0];
		
		if (line.isEmpty()) {
			return null;
		}
		
		if (!Character.isJavaIdentifierStart(line.charAt(0))) {
			throw new ServiceConfigurationError(service.getName() + ": Illegal provider-class name: " + line);
		}
		
		for (int i = 1; i < line.length(); i++) {
			if (!Character.isJavaIdentifierPart(line.charAt(i))) {
				throw new ServiceConfigurationError(service.getName() + ": Illegal provider-class name: " + line);
			}
		}
		
		return line;
	}
}
