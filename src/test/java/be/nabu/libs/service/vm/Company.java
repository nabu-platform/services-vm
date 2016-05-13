package be.nabu.libs.service.vm;

import java.util.List;

public class Company {
	private List<Employee> employees;
	private String name;

	public List<Employee> getEmployees() {
		return employees;
	}

	public void setEmployees(List<Employee> employees) {
		this.employees = employees;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
