package be.nabu.libs.services.vm.api;

import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.validator.api.Validation;

public interface Step {
	public void execute(VMContext context) throws ServiceException;
	
	@XmlAttribute
	public String getId();
	public void setId(String id);
	
	@XmlTransient
	public StepGroup getParent();
	public void setParent(StepGroup parent);

	@XmlAttribute
	public String getComment();
	public void setComment(String comment);
	
	@XmlTransient
	public VMService getServiceDefinition();
	
	public List<Validation<?>> validate(ServiceContext context);
	
	@XmlAttribute
	public String getLabel();
	public void setLabel(String label);
	
	@XmlAttribute
	public boolean isDisabled();
	public void setDisabled(boolean isDisabled);
	
	/**
	 * Refresh this step
	 */
	public void refresh();
}
