/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.services.vm.step;

import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import be.nabu.libs.authentication.impl.ImpersonateToken;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.validator.api.Validation;

/**
 * Allows you to rethrow an existing exception or throw a new one
 * 
 * @author alex
 *
 */
@XmlType(propOrder = { "code", "message", "data", "alias", "realm", "authenticationId", "whitelist" })
public class Throw extends BaseStep {
	
	public Throw() {
		// automatic creation
	}
	
	public Throw(String message) {
		this.message = message;
	}

	/**
	 * The message is meant to be a generic description of the problem, e.g. "You do not have enough credit."
	 * This message is evaluated against the pipeline if you start it with "="
	 * ="Can not find " + b/value + " in this"
	 */
	private String message;
	/**
	 * The description is meant to be a more detailed description of this particular problem instance, e.g. "Your current balance is 30, but that costs 50."
	 * This description is evaluated against the pipeline if you start it with "="
	 * ="Can not find " + b/value + " in this"
	 */
	
	/**
	 * A reference code 
	 */
	private String code;
	
	/**
	 * You can include structured data into your exception
	 */
	private String data;
	
	/**
	 * You can prewhitelist a code to be shown in the web application, so you don't have to centrally register it
	 */
	private boolean whitelist;
	
	/**
	 * For those cases (rather few) when you throw an exception for a different user then then current token
	 * The most notable usecase is when you throw exceptions _while_ validating the user, you throw exceptions about them but they are not currently the active user
	 */
	private String alias, realm, authenticationId;
	
	@Override
	public void execute(VMContext context) throws ServiceException {
		Object messageValue = null;
		Object descriptionValue = null;
		Object codeValue = null;
		Object dataValue = null;
		if (message != null) {
			if (message.startsWith("=")) {
				messageValue = getVariable(context.getServiceInstance().getPipeline(), message.substring(1));
			}
			else {
				messageValue = message;
			}
		}
		if (data != null) {
			if (data.startsWith("=")) {
				dataValue = getVariable(context.getServiceInstance().getPipeline(), data.substring(1));
			}
			else {
				dataValue = data;
			}
		}
		String description = getDescription();
		if (description != null) {
			if (description.startsWith("=")) {
				descriptionValue = getVariable(context.getServiceInstance().getPipeline(), description.substring(1));
			}
			else {
				descriptionValue = description;
			}
		}
		if (code != null) {
			if (code.startsWith("=")) {
				codeValue = getVariable(context.getServiceInstance().getPipeline(), code.substring(1));
			}
			else {
				codeValue = code;
			}
		}
		// if we have a service exception and we don't want to add a code to it, just rethrow
		if (messageValue instanceof ServiceException && codeValue == null && descriptionValue == null && alias == null) {
			if (whitelist) {
				((ServiceException) messageValue).setWhitelisted(whitelist);
			}
			if (dataValue != null) {
				((ServiceException) messageValue).setData(dataValue);
			}
			((ServiceException) messageValue).setExplicit(true);
			if (((ServiceException) messageValue).getSourceId() == null) {
				((ServiceException) messageValue).setSourceId(getId());
			}
			throw ((ServiceException) messageValue);
		}
		// any other exception is wrapped
		else if (messageValue instanceof Exception) {
			ServiceException serviceException = new ServiceException(codeValue == null ? null : codeValue.toString(), (String) null, (Exception) messageValue);
			serviceException.setExplicit(true);
			serviceException.setSourceId(getId());
			serviceException.setDescription(descriptionValue == null ? null : descriptionValue.toString());
			if (whitelist) {
				serviceException.setWhitelisted(whitelist);
			}
			if (dataValue != null) {
				serviceException.setData(dataValue);
			}
			enrichToken(context, serviceException);
			throw serviceException;
		}
		else {
			ServiceException serviceException = new ServiceException(codeValue == null ? null : codeValue.toString(), messageValue == null ? "No message" : messageValue.toString(), context.getCaughtException());
			serviceException.setExplicit(true);
			serviceException.setSourceId(getId());
			serviceException.setDescription(descriptionValue == null ? null : descriptionValue.toString());
			if (whitelist) {
				serviceException.setWhitelisted(whitelist);
			}
			if (dataValue != null) {
				serviceException.setData(dataValue);
			}
			enrichToken(context, serviceException);
			throw serviceException;
		}
	}

	private void enrichToken(VMContext context, ServiceException serviceException) throws ServiceException {
		// we have a different user
		if (alias != null) {
			Object alias;
			if (this.alias.startsWith("=")) {
				alias = getVariable(context.getServiceInstance().getPipeline(), this.alias.substring(1));
			}
			else {
				alias = this.alias;
			}
			Object realm;
			if (this.realm != null && this.realm.startsWith("=")) {
				realm = getVariable(context.getServiceInstance().getPipeline(), this.realm.substring(1));
			}
			else {
				realm = this.realm;
			}
			Object authenticationId;
			if (this.authenticationId != null && this.authenticationId.startsWith("=")) {
				authenticationId = getVariable(context.getServiceInstance().getPipeline(), this.authenticationId.substring(1));
			}
			else {
				authenticationId = this.authenticationId;
			}
			if (alias != null) {
				ImpersonateToken token = new ImpersonateToken(null, realm == null ? null : realm.toString(), alias == null ? null : alias.toString());
				token.setAuthenticationId(authenticationId == null ? null : authenticationId.toString());
				serviceException.setToken(token);
			}
		}
	}
	
	@XmlAttribute
	public boolean isWhitelist() {
		return whitelist;
	}
	public void setWhitelist(boolean whitelist) {
		this.whitelist = whitelist;
	}

	@Override
	public List<Validation<?>> validate(ServiceContext serviceContext) {
		List<Validation<?>> messages = super.validate(serviceContext);
		if (message != null && message.startsWith("=")) {
			messages.addAll(validateQuery(serviceContext, message.substring(1)));
		}
		if (code != null && code.startsWith("=")) {
			messages.addAll(validateQuery(serviceContext, code.substring(1)));
		}
		return messages;
	}

	@XmlAttribute
	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
	
	@Override
	public void refresh() {
		// do nothing
	}

	@XmlAttribute
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}

	@XmlAttribute
	public String getAlias() {
		return alias;
	}
	public void setAlias(String alias) {
		this.alias = alias;
	}

	@XmlAttribute
	public String getRealm() {
		return realm;
	}
	public void setRealm(String realm) {
		this.realm = realm;
	}

	@XmlAttribute
	public String getAuthenticationId() {
		return authenticationId;
	}
	public void setAuthenticationId(String authenticationId) {
		this.authenticationId = authenticationId;
	}

	@XmlAttribute
	public String getData() {
		return data;
	}
	public void setData(String data) {
		this.data = data;
	}
	
}
