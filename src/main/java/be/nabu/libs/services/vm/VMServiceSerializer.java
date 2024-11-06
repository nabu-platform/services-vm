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

import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.step.Catch;
import be.nabu.libs.services.vm.step.Finally;
import be.nabu.libs.services.vm.step.Sequence;

public class VMServiceSerializer {
	
	public void marshal(Sequence sequence, OutputStream output) throws JAXBException {
		Document document = newDocument(false);
		marshal(sequence, document);
	}
	
	private void marshal(Sequence sequence, Node parent) {
		Element sequenceElement = newElement(parent, "sequence");
		Element tryElement = newElement(sequenceElement, "try");
		marshal(tryElement, sequence.getChildren());
	}
	
	private void marshal(Catch catchClause, Node parent) {
		Element catchElement = newElement(parent, "catch");
		String type = "";
		for (Class<?> catchType : catchClause.getTypes()) {
			if (!type.isEmpty())
				type += " | ";
			type += catchType.getName();
		}
		if (!type.isEmpty())
			catchElement.setAttribute("type", type);
		if (catchClause.getVariable() != null)
			catchElement.setAttribute("variable", catchClause.getVariable());
		marshal(catchElement, catchClause.getChildren());
	}
	
	private void marshal(Finally finallyClause, Node parent) {
		Element finallyElement = newElement(parent, "finally");
		marshal(finallyElement, finallyClause.getChildren());
	}
	
	private void marshal(Node parent, List<? extends Step> children) {
		for (Step child : children) {
			if (child instanceof Sequence)
				marshal((Sequence) child, parent);
			else if (child instanceof Catch)
				marshal((Catch) child, parent);
			else if (child instanceof Finally)
				marshal((Finally) child, parent);
		}
	}
	
	private void marshal(Node parent, Step...children) {
		marshal(parent, Arrays.asList(children));
	}
	
	private Element newElement(Node parent, String name) {
		Element element = parent.getOwnerDocument().createElement(name);
		parent.appendChild(element);
		return element;
	}
	
	public static Document newDocument(boolean namespaceAware) {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(false);
		factory.setNamespaceAware(namespaceAware);
		try {
			return factory.newDocumentBuilder().newDocument();
		}
		catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
	}
}
