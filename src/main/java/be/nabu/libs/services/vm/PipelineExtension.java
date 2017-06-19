package be.nabu.libs.services.vm;

import java.util.List;

import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.api.ModifiableElement;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.TemporaryProperty;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.validator.api.ValidationMessage;

public class PipelineExtension extends Structure {
	/**
	 * Make sure any manual changes (as in you manually add a field) are pushed to the root pipeline
	 */
	@Override
	public List<ValidationMessage> add(Element<?> element) {
		return add(element, true);
	}
	
	public List<ValidationMessage> add(Element<?> element, boolean setInSuper) {
		if (setInSuper && getSuperType() != null) {
			if (element instanceof ModifiableElement) {
				((ModifiableElement<?>) element).setParent((ComplexType) getSuperType());
			}
			if (getSuperType() instanceof PipelineExtension) {
				return ((PipelineExtension) getSuperType()).add(element, setInSuper);
			}
			else {
				return ((ModifiableComplexType) getSuperType()).add(element);
			}
		}
		else {
			// mark it as a temporary property
			element.setProperty(new ValueImpl<Boolean>(TemporaryProperty.getInstance(), true));
			return super.add(element);
		}
	}
}