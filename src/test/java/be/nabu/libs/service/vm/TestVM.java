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

package be.nabu.libs.service.vm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Date;

import javax.xml.bind.JAXBException;

import junit.framework.TestCase;
import be.nabu.libs.services.ServiceUtils;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.vm.SimpleVMServiceDefinition;
import be.nabu.libs.services.vm.step.For;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.services.vm.step.Map;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.base.RootElement;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.xml.XMLBinding;
import be.nabu.libs.types.binding.xml.XMLMarshaller;
import be.nabu.libs.types.java.BeanInstance;
import be.nabu.libs.types.java.BeanType;

public class TestVM extends TestCase {

	public void testNothing() throws ServiceException {
		SimpleVMServiceDefinition definition = new SimpleVMServiceDefinition(
			new BeanType<Rolodex>(Rolodex.class),
			new BeanType<Company>(Company.class)
		);
		
		definition.setRoot(new Sequence(definition,
			new Map(
				new Link("input/contacts", "output/employees")
			)
		));
		
		Rolodex rolodex = new Rolodex();
		rolodex.getContacts().add(new Person("John", new Date()));
		rolodex.getContacts().add(new Person("Joe", new Date()));
		
		Company company = TypeUtils.getAsBean(
			definition.newInstance().execute(ServiceUtils.newExecutionContext(), new BeanInstance<Rolodex>(rolodex)),
			Company.class
		);
		
		// you can't actually access the employees because they are of type Person. trying to get one of them would result in a classcastexceptions
		// the only reason this actually works is because you map the entire list
		System.out.println(company.getEmployees());
	}
	
	public void testSomething() throws ServiceException, JAXBException, IOException, ParseException {
		SimpleVMServiceDefinition definition = new SimpleVMServiceDefinition(
			new BeanType<Rolodex>(Rolodex.class),
			new BeanType<Company>(Company.class)
		);
		
		definition.setRoot(new Sequence(definition,
			new For(
				new Map(
					// you have to reference it as "/i" instead of "i", otherwise it will try to look in the Company class (output is of type Company here) for i
					// with "/i" you force the code to look for i at the root which is where the for loop will inject it
					new Link("contact", "output/employees[/i]")
				)
			).setQuery("input/contacts[name ~ 'J.*']")
				// each result will be put on the pipeline using this variable name
				.setVariable("contact")
				// the index of the current result will also be injected into the pipeline, as "i"
				.setIndex("i")
		));

		// this tests marshalling & unmarshalling and then executing
		XMLBinding binding = new XMLBinding(new BeanType<Sequence>(Sequence.class), Charset.forName("UTF-8"));
		XMLMarshaller marshaller = new XMLMarshaller(new RootElement(new BeanType<Sequence>(Sequence.class)));
		marshaller.setNamespaceAware(false);

		StringWriter writer = new StringWriter();
		marshaller.marshal(writer, new BeanInstance<Sequence>(definition.getRoot()));
		String content = writer.toString();
		ComplexContent complexContent = binding.unmarshal(new ByteArrayInputStream(content.getBytes("UTF-8")), new Window[0]);
		definition.setRoot(TypeUtils.getAsBean(
			complexContent,
			Sequence.class));
		
		Rolodex rolodex = new Rolodex();
		// if you set Person objects here instead of Employee, you will get classcastexceptions because you are assigning them one at a time instead of the whole list
		// the code will check the generic definition of the list to see if the objects are of the right type
		rolodex.getContacts().add(new Employee("John", new Date()));
		rolodex.getContacts().add(new Employee("Joe", new Date()));
		rolodex.getContacts().add(new Employee("Bob", new Date()));

		Company company = TypeUtils.getAsBean(
			definition.newInstance().execute(ServiceUtils.newExecutionContext(), new BeanInstance<Rolodex>(rolodex)),
			Company.class
		);

		System.out.println(company.getEmployees());
		
	}
	
}
