package be.nabu.libs.service.vm;

import java.util.ArrayList;
import java.util.List;

public class Rolodex {
	private List<Person> contacts = new ArrayList<Person>();

	public List<Person> getContacts() {
		return contacts;
	}

	public void setContacts(List<Person> contacts) {
		this.contacts = contacts;
	}
	
}
