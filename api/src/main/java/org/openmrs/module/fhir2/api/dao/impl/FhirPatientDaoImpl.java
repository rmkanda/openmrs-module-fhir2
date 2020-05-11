/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.fhir2.api.dao.impl;

import static org.hibernate.criterion.Restrictions.and;
import static org.hibernate.criterion.Restrictions.eq;
import static org.hibernate.criterion.Restrictions.or;
import static org.hl7.fhir.r4.model.Patient.SP_DEATH_DATE;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;

import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.StringOrListParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import lombok.AccessLevel;
import lombok.Setter;
import org.hibernate.Criteria;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.dao.FhirPatientDao;
import org.openmrs.module.fhir2.api.search.param.PropParam;
import org.openmrs.module.fhir2.api.search.param.SearchParameterMap;
import org.springframework.stereotype.Component;

@Component
@Setter(AccessLevel.PACKAGE)
public class FhirPatientDaoImpl extends BasePersonDao<Patient> implements FhirPatientDao {
	
	@Override
	public Patient getPatientById(Integer id) {
		return (Patient) getSessionFactory().getCurrentSession().createCriteria(Patient.class).add(eq("patientId", id))
		        .uniqueResult();
	}
	
	@Override
	public Patient getPatientByUuid(String uuid) {
		return (Patient) getSessionFactory().getCurrentSession().createCriteria(Patient.class).add(eq("uuid", uuid))
		        .uniqueResult();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public PatientIdentifierType getPatientIdentifierTypeByNameOrUuid(String name, String uuid) {
		List<PatientIdentifierType> identifierTypes = (List<PatientIdentifierType>) getSessionFactory().getCurrentSession()
		        .createCriteria(PatientIdentifierType.class)
		        .add(or(and(eq("name", name), eq("retired", false)), eq("uuid", uuid))).list();
		
		if (identifierTypes.isEmpty()) {
			return null;
		} else {
			// favour uuid if one was supplied
			if (uuid != null) {
				try {
					return identifierTypes.stream().filter((idType) -> uuid.equals(idType.getUuid())).findFirst()
					        .orElse(identifierTypes.get(0));
				}
				catch (NoSuchElementException ignored) {}
			}
			
			return identifierTypes.get(0);
		}
	}
	
	@Override
	protected void setupSearchParams(Criteria criteria, SearchParameterMap theParams) {
		theParams.getParameters().forEach(entry -> {
			switch (entry.getKey()) {
				case FhirConstants.NAME_SEARCH_HANDLER:
					handleNames(entry.getValue(), criteria);
					break;
				case FhirConstants.GENDER_SEARCH_HANDLER:
					entry.getValue().forEach(
					    p -> handleGender(p.getPropertyName(), (TokenOrListParam) p.getParam()).ifPresent(criteria::add));
					break;
				case FhirConstants.IDENTIFIER_SEARCH_HANDLER:
					entry.getValue()
					        .forEach(identifier -> handleIdentifier(criteria, (TokenOrListParam) identifier.getParam()));
					break;
				case FhirConstants.DATE_RANGE_SEARCH_HANDLER:
					entry.getValue().forEach(dateRangeParam -> handleDateRange(dateRangeParam.getPropertyName(),
					    (DateRangeParam) dateRangeParam.getParam()).ifPresent(criteria::add));
					break;
				case FhirConstants.BOOLEAN_SEARCH_HANDLER:
					entry.getValue().forEach(
					    b -> handleBoolean(b.getPropertyName(), (TokenOrListParam) b.getParam()).ifPresent(criteria::add));
					break;
				case FhirConstants.ADDRESS_SEARCH_HANDLER:
					handleAddresses(criteria, entry);
					break;
			}
		});
	}
	
	private void handleAddresses(Criteria criteria, Map.Entry<String, List<PropParam<?>>> entry) {
		AtomicReference<StringOrListParam> city = new AtomicReference<>();
		AtomicReference<StringOrListParam> country = new AtomicReference<>();
		AtomicReference<StringOrListParam> postalCode = new AtomicReference<>();
		AtomicReference<StringOrListParam> state = new AtomicReference<>();
		entry.getValue().forEach(d -> {
			switch (d.getPropertyName()) {
				case FhirConstants.CITY_PROPERTY:
					city.set((StringOrListParam) d.getParam());
					break;
				case FhirConstants.COUNTRY_PROPERTY:
					country.set((StringOrListParam) d.getParam());
					break;
				case FhirConstants.POSTAL_CODE_PROPERTY:
					postalCode.set((StringOrListParam) d.getParam());
					break;
				case FhirConstants.STATE_PROPERTY:
					state.set((StringOrListParam) d.getParam());
					break;
			}
		});
		
		handlePersonAddress("pad", city.get(), state.get(), postalCode.get(), country.get()).ifPresent(c -> {
			criteria.createAlias("addresses", "pad");
			criteria.add(c);
		});
	}
	
	@Override
	protected String getSqlAlias() {
		return "this_1_";
	}
	
	@Override
	protected String paramToProp(String param) {
		if (param.equalsIgnoreCase(SP_DEATH_DATE)) {
			return "deathDate";
		}
		
		return super.paramToProp(param);
	}
	
	private void handleNames(List<PropParam<?>> params, Criteria criteria) {
		params.forEach(param -> {
			switch (param.getPropertyName()) {
				case FhirConstants.NAME_PROPERTY:
					handleNames(criteria, (StringOrListParam) param.getParam(), null, null);
					break;
				case FhirConstants.GIVEN_PROPERTY:
					handleNames(criteria, null, (StringOrListParam) param.getParam(), null);
					break;
				case FhirConstants.FAMILY_PROPERTY:
					handleNames(criteria, null, null, (StringOrListParam) param.getParam());
					break;
			}
		});
	}
}
