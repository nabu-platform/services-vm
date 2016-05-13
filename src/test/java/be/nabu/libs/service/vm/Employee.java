package be.nabu.libs.service.vm;

import java.util.Date;

public class Employee extends Person {
	private static int counter = 0;
	private int id;
	
	public Employee() {
		
	}
	
	public Employee(String name, Date dateOfBirth) {
		super(name, dateOfBirth);
		this.id = counter++;
	}
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	
	@Override
	public String toString() {
		return id + ": " + super.toString();
	}
}
