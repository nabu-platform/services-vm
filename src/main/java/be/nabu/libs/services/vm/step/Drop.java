package be.nabu.libs.services.vm.step;

import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;

import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.VMContext;
import be.nabu.libs.validator.api.Validation;

public class Drop extends BaseStep {

	private String path;
	
	@Override
	public void execute(VMContext context) throws ServiceException {
		setVariable(context.getServiceInstance().getPipeline(), path, null);
	}

	@Override
	public void refresh() {
		// do nothing
	}

	@XmlAttribute
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}

	@Override
	public List<Validation<?>> validate(ServiceContext serviceContext) {
		List<Validation<?>> messages = super.validate(serviceContext);
		messages.addAll(validateQuery(serviceContext, path));
		return messages;
	}

	
}
