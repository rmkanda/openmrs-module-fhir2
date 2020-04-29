/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.fhir2;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import ca.uhn.fhir.rest.server.IResourceProvider;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.Module;
import org.openmrs.module.ModuleException;
import org.openmrs.module.ModuleFactory;
import org.openmrs.module.fhir2.api.FhirService;
import org.openmrs.module.fhir2.api.dao.FhirDao;
import org.openmrs.module.fhir2.api.spi.ServiceClassLoader;
import org.openmrs.module.fhir2.api.translators.FhirTranslator;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

/**
 * This class contains the logic that is run every time this module is either started or shutdown
 */
@Slf4j
@SuppressWarnings("unused")
public class FhirActivator extends BaseModuleActivator implements ApplicationContextAware {
	
	private ApplicationContext applicationContext;
	
	private AnnotationConfigApplicationContext childApplicationContext;
	
	@Override
	public void started() {
		if (applicationContext == null) {
			throw new ModuleException("Cannot load FHIR2 module as the main application context is not available");
		}
		
		childApplicationContext = new AnnotationConfigApplicationContext();
		childApplicationContext.setParent(applicationContext);
		
		Set<Class<?>> services = new LinkedHashSet<>();
		
		for (Module module : ModuleFactory.getLoadedModules()) {
			ClassLoader cl = ModuleFactory.getModuleClassLoader(module);
			
			Stream.of(FhirDao.class, FhirTranslator.class, FhirService.class, IResourceProvider.class)
			        .flatMap(c -> new ServiceClassLoader<>(c, cl).load().stream()).filter(c -> {
				        boolean result;
				        try {
					        result = c.getAnnotation(Component.class) != null;
				        }
				        catch (NullPointerException e) {
					        result = false;
				        }
				        
				        if (!result) {
					        log.warn("Skipping {} as it is not an annotated Spring Component", c);
				        }
				        
				        return result;
			        }).forEach(services::add);
		}
		
		childApplicationContext.register(services.toArray(new Class<?>[0]));
		
		log.info("Started FHIR");
	}
	
	@Override
	public void stopped() {
		log.info("Shutdown FHIR");
	}
	
	public ApplicationContext getApplicationContext() {
		if (childApplicationContext == null) {
			throw new IllegalStateException("This method cannot be called before the module is started");
		}
		
		return childApplicationContext;
	}
	
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
