/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.service.access.control.profile;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.security.access.control.AccessControlPolicy;
import com.liferay.portal.kernel.security.access.control.AccessControlled;
import com.liferay.portal.kernel.security.access.control.BaseAccessControlPolicy;
import com.liferay.portal.kernel.security.access.control.profile.ServiceAccessControlProfileThreadLocal;
import com.liferay.portal.kernel.util.CharPool;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.security.auth.CompanyThreadLocal;
import com.liferay.service.access.control.profile.model.SACPEntry;
import com.liferay.service.access.control.profile.service.SACPEntryLocalService;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Mika Koivisto
 */
@Component(service = AccessControlPolicy.class)
public class SACPAccessControlPolicy extends BaseAccessControlPolicy {

	@Override
	public void onServiceRemoteAccess(
			Method method, Object[] arguments,
			AccessControlled accessControlled)
		throws SecurityException {

		List<String> serviceAccessControlProfileNames =
			ServiceAccessControlProfileThreadLocal.
				getActiveServiceAccessControlProfileNames();

		if (serviceAccessControlProfileNames == null) {
			return;
		}

		long companyId = CompanyThreadLocal.getCompanyId();

		Set<String> allowedServices = new HashSet<>();

		for (String name : serviceAccessControlProfileNames) {
			try {
				SACPEntry sacpEntry = _sacpEntryLocalService.getSACPEntry(
					companyId, name);

				allowedServices.addAll(sacpEntry.getAllowedServicesList());
			}
			catch (PortalException pe) {
				throw new SecurityException(pe);
			}
		}

		Class<?> clazz = method.getDeclaringClass();

		String className = clazz.getName();

		if (allowedServices.contains(className)) {
			return;
		}

		String methodName = method.getName();

		String classNameAndMethodName = className.concat(
			StringPool.POUND).concat(methodName);

		if (allowedServices.contains(classNameAndMethodName)) {
			return;
		}

		for (String allowedService : allowedServices) {
			if (matches(className, methodName, allowedService)) {
				return;
			}
		}

		throw new SecurityException(
			"Access denied to " + classNameAndMethodName);
	}

	protected boolean matches(
		String className, String methodName, String allowedService) {

		String allowedClassName = null;
		String allowedMethodName = null;

		int index = allowedService.indexOf(CharPool.POUND);

		if (index > -1) {
			allowedClassName = allowedService.substring(0, index);
			allowedMethodName = allowedService.substring(index + 1);
		}
		else {
			allowedClassName = allowedService;
		}

		boolean wildcardMatchClass = false;

		if (Validator.isNotNull(allowedClassName) &&
			allowedClassName.endsWith(StringPool.STAR)) {

			allowedClassName = allowedClassName.substring(
				0, allowedClassName.length() - 1);
			wildcardMatchClass = true;
		}

		boolean wildcardMatchMethod = false;

		if (Validator.isNotNull(allowedMethodName) &&
			allowedMethodName.endsWith(StringPool.STAR)) {

			allowedMethodName = allowedMethodName.substring(
				0, allowedMethodName.length() - 1);
			wildcardMatchMethod = true;
		}

		if (Validator.isNotNull(allowedClassName) &&
			Validator.isNotNull(allowedMethodName)) {

			if (wildcardMatchClass && !className.startsWith(allowedClassName)) {
				return false;
			}
			else if (!wildcardMatchClass &&
					 !className.equals(allowedClassName)) {

				return false;
			}

			if (wildcardMatchMethod &&
				!methodName.startsWith(allowedMethodName)) {

				return false;
			}
			else if (!wildcardMatchMethod &&
					 !methodName.equals(allowedMethodName)) {

				return false;
			}

			return true;
		}
		else if (Validator.isNotNull(allowedClassName)) {
			if (wildcardMatchClass && !className.startsWith(allowedClassName)) {
				return false;
			}
			else if (!wildcardMatchClass &&
					 !className.equals(allowedClassName)) {

				return false;
			}

			return true;
		}
		else if (Validator.isNotNull(allowedMethodName)) {
			if (wildcardMatchMethod &&
				!methodName.startsWith(allowedMethodName)) {

				return false;
			}
			else if (!wildcardMatchMethod &&
					 !methodName.equals(allowedMethodName)) {

				return false;
			}

			return true;
		}
		else if (wildcardMatchClass && Validator.isNull(allowedClassName)) {
			return true;
		}

		return false;
	}

	@Reference(unbind = "-")
	protected void setSACPEntryLocalService(
		SACPEntryLocalService sacpEntryLocalService) {

		_sacpEntryLocalService = sacpEntryLocalService;
	}

	private SACPEntryLocalService _sacpEntryLocalService;

}