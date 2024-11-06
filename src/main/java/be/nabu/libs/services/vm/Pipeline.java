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

package be.nabu.libs.services.vm;

import java.util.List;
import java.util.Set;

import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.ModifiableType;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.NameProperty;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.types.structure.SuperTypeProperty;
import be.nabu.libs.validator.api.ValidationMessage;

public class Pipeline extends Structure {

	public static final String INPUT = "input";
	public static final String OUTPUT = "output";
	
	public Pipeline(Service definition) {
		this(definition.getServiceInterface());
	}
	
	public Pipeline(ComplexType input, ComplexType output) {
		this("pipeline", input, output);
	}
	
	public Pipeline(ServiceInterface serviceInterface) {
		this("pipeline", serviceInterface.getInputDefinition(), serviceInterface.getOutputDefinition());
	}
	
	private Pipeline(String name, ComplexType input, ComplexType output) {
		setName(name);
		if (input != null) {
			add(new ComplexElementImpl(input, this, new ValueImpl<String>(new NameProperty(), INPUT)));
		}
		if (output != null) {
			add(new ComplexElementImpl(output, this, new ValueImpl<String>(new NameProperty(), OUTPUT)));
		}
	}

	@Override
	public List<ValidationMessage> add(Element<?> element) {
		// if the input/output is added to this pipeline, it is likely unnamed because while parsing the name is set on the element, not the type
		// however we need the input and output to be named types as they are marshalled/unmarshalled without the pipeline parent
		if (element.getName().equals(INPUT) && element.getType().getName() == null && element.getType() instanceof ModifiableType) {
			DefinedServiceInterface newInterface = ValueUtils.getValue(PipelineInterfaceProperty.getInstance(), getProperties());
			((ModifiableType) element.getType()).setProperty(new ValueImpl<Type>(SuperTypeProperty.getInstance(), newInterface == null ? null : newInterface.getInputDefinition()));
			((ModifiableType) element.getType()).setProperty(new ValueImpl<String>(new NameProperty(), INPUT));
		}
		if (element.getName().equals(OUTPUT) && element.getType().getName() == null && element.getType() instanceof ModifiableType) {
			DefinedServiceInterface newInterface = ValueUtils.getValue(PipelineInterfaceProperty.getInstance(), getProperties());
			((ModifiableType) element.getType()).setProperty(new ValueImpl<Type>(SuperTypeProperty.getInstance(), newInterface == null ? null : newInterface.getOutputDefinition()));
			((ModifiableType) element.getType()).setProperty(new ValueImpl<String>(new NameProperty(), OUTPUT));
		}
		return super.add(element);
	}

	@Override
	public Set<Property<?>> getSupportedProperties(Value<?>... values) {
		Set<Property<?>> properties = super.getSupportedProperties(values);
		properties.add(PipelineInterfaceProperty.getInstance());
		return properties;
	}

	@Override
	public void setProperty(Value<?>... values) {
		for (Value<?> value : values) {
			super.setProperty(values);
			if (value.getProperty().getClass().equals(PipelineInterfaceProperty.class)) {
				DefinedServiceInterface newInterface = (DefinedServiceInterface) value.getValue();
				Element<?> element = get(INPUT);
				if (element != null) {
					((ModifiableType) element.getType()).setProperty(new ValueImpl<Type>(SuperTypeProperty.getInstance(), newInterface == null ? null : newInterface.getInputDefinition()));
				}
				element = get(OUTPUT);
				if (element != null) {
					((ModifiableType) element.getType()).setProperty(new ValueImpl<Type>(SuperTypeProperty.getInstance(), newInterface == null ? null : newInterface.getOutputDefinition()));
				}
			}
		}
	}
	
}
