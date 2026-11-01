package hems.model;

import hems.util.Validator;

/**
 * Common data for everyone in the system (inheritance: Patient and Doctor extend this).
 * Fields are private and final (encapsulation); validation is delegated to Validator.
 */
public abstract class Person {
    private final String id;
    private final String name;
    private final int age;

    protected Person(String id, String name, int age, int minAge, int maxAge) {
        this.id = Validator.validateId(id, "ID");
        this.name = Validator.validateName(name);
        this.age = Validator.validateAge(age, minAge, maxAge);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    /** Polymorphism: each subclass names its own role. */
    public abstract String getRole();

    @Override
    public String toString() {
        return getRole() + " [ID: " + id + ", Name: " + name + ", Age: " + age + "]";
    }
}
