package tut.ac.za.AgriFinanceAPIs.supplier.dto;
public class SupplierRequest {
    private String name, location, contact;
    public SupplierRequest(){}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getLocation(){return location;} public void setLocation(String v){location=v;}
    public String getContact(){return contact;} public void setContact(String v){contact=v;}
}
