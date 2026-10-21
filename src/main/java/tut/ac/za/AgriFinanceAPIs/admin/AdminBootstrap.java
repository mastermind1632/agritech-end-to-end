package tut.ac.za.AgriFinanceAPIs.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tut.ac.za.AgriFinanceAPIs.common.Roles;
import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;

/**
 * Creates (or promotes) the first administrator from APP_ADMIN_CONTACT and APP_ADMIN_PASSWORD at start-up.
 * After that, admins grant roles to others in the admin console. Leave the variables empty to skip.
 */
@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final FarmerRepository farmers;
    private final PasswordEncoder encoder;
    private final String contact;
    private final String password;
    private final String name;

    public AdminBootstrap(FarmerRepository farmers, PasswordEncoder encoder,
                          @Value("${app.bootstrap.admin-contact:}") String contact,
                          @Value("${app.bootstrap.admin-password:}") String password,
                          @Value("${app.bootstrap.admin-name:Platform Administrator}") String name) {
        this.farmers = farmers;
        this.encoder = encoder;
        this.contact = contact;
        this.password = password;
        this.name = name;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (contact == null || contact.isBlank()) return;
        Farmer f = farmers.findByContact(contact.trim()).orElse(null);
        if (f == null) {
            if (password == null || password.length() < 8) {
                log.warn("APP_ADMIN_CONTACT is set but APP_ADMIN_PASSWORD is missing or shorter than 8 characters; no admin created");
                return;
            }
            f = new Farmer();
            f.setName(name);
            f.setContact(contact.trim());
            f.setPasswordHash(encoder.encode(password));
            f.setRole(Roles.ADMIN);
            farmers.save(f);
            log.info("Created administrator account {}", contact);
        } else if (!Roles.ADMIN.equals(f.getRole())) {
            f.setRole(Roles.ADMIN);
            farmers.save(f);
            log.info("Promoted {} to administrator", contact);
        }
    }
}
