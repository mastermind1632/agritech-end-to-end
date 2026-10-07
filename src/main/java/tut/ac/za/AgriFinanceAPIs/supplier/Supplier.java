package tut.ac.za.AgriFinanceAPIs.supplier;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="suppliers")
public class Supplier {
    @Id @Column(length=36)
    private String id;
    @Column(nullable=false,length=150) private String name;
    @Column(length=150) private String location;
    @Column(length=100) private String contact;

    @PrePersist public void prePersist(){ if(id==null) id=UUID.randomUUID().toString(); }
    public String getId(){return id;} public void setId(String id){this.id=id;}
    public String getName(){return name;} public void setName(String name){this.name=name;}
    public String getLocation(){return location;} public void setLocation(String location){this.location=location;}
    public String getContact(){return contact;} public void setContact(String contact){this.contact=contact;}
}
