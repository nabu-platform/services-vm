package be.nabu.libs.service.vm;

import java.util.Date;

public class Person {
	private Date dateOfBirth;
	private String name;
	
	public Person() {
		
	}
	
	public Person(String name, Date dateOfBirth) {
		this.name = name;
		this.dateOfBirth = dateOfBirth;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Date getDateOfBirth() {
		return dateOfBirth;
	}
	public void setDateOfBirth(Date dateOfBirth) {
		this.dateOfBirth = dateOfBirth;
	}
	@Override
	public String toString() {
		return name + " [" + dateOfBirth + "]";
	}
}
