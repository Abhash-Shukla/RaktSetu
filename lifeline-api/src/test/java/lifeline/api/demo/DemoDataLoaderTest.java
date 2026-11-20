package lifeline.api.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import lifeline.api.model.*;
import lifeline.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataLoaderTest {

    private UserRepository userRepository;
    private DonorProfileRepository donorProfileRepository;
    private BankRepository bankRepository;
    private BankInventoryRepository bankInventoryRepository;
    private BloodRequestRepository bloodRequestRepository;
    private PasswordEncoder passwordEncoder;
    private ObjectMapper objectMapper;
    private DemoDataLoader demoDataLoader;

    private final List<UserEntity> savedUsers = new ArrayList<>();
    private final List<BankEntity> savedBanks = new ArrayList<>();
    private final List<DonorProfileEntity> savedProfiles = new ArrayList<>();
    private final List<BloodRequestEntity> savedRequests = new ArrayList<>();

    @BeforeEach
    void setUp() {
        savedUsers.clear();
        savedBanks.clear();
        savedProfiles.clear();
        savedRequests.clear();

        userRepository = (UserRepository) Proxy.newProxyInstance(
            UserRepository.class.getClassLoader(),
            new Class<?>[]{UserRepository.class},
            (proxy, method, args) -> {
                String name = method.getName();
                if ("existsByUsername".equals(name)) {
                    String uname = (String) args[0];
                    return savedUsers.stream().anyMatch(u -> uname.equals(u.getUsername()));
                }
                if ("findByUsername".equals(name)) {
                    String uname = (String) args[0];
                    return savedUsers.stream().filter(u -> uname.equals(u.getUsername())).findFirst();
                }
                if ("save".equals(name)) {
                    UserEntity u = (UserEntity) args[0];
                    u.setId((long) (savedUsers.size() + 1));
                    savedUsers.add(u);
                    return u;
                }
                return null;
            }
        );

        donorProfileRepository = (DonorProfileRepository) Proxy.newProxyInstance(
            DonorProfileRepository.class.getClassLoader(),
            new Class<?>[]{DonorProfileRepository.class},
            (proxy, method, args) -> {
                String name = method.getName();
                if ("count".equals(name)) {
                    return 0L;
                }
                if ("save".equals(name)) {
                    DonorProfileEntity p = (DonorProfileEntity) args[0];
                    p.setId((long) (savedProfiles.size() + 1));
                    savedProfiles.add(p);
                    return p;
                }
                return null;
            }
        );

        bankRepository = (BankRepository) Proxy.newProxyInstance(
            BankRepository.class.getClassLoader(),
            new Class<?>[]{BankRepository.class},
            (proxy, method, args) -> {
                String name = method.getName();
                if ("count".equals(name)) {
                    return 0L;
                }
                if ("save".equals(name)) {
                    BankEntity b = (BankEntity) args[0];
                    b.setId((long) (savedBanks.size() + 1));
                    savedBanks.add(b);
                    return b;
                }
                return null;
            }
        );

        bankInventoryRepository = (BankInventoryRepository) Proxy.newProxyInstance(
            BankInventoryRepository.class.getClassLoader(),
            new Class<?>[]{BankInventoryRepository.class},
            (proxy, method, args) -> {
                if ("save".equals(method.getName())) {
                    return args[0];
                }
                return null;
            }
        );

        bloodRequestRepository = (BloodRequestRepository) Proxy.newProxyInstance(
            BloodRequestRepository.class.getClassLoader(),
            new Class<?>[]{BloodRequestRepository.class},
            (proxy, method, args) -> {
                String name = method.getName();
                if ("count".equals(name)) {
                    return 0L;
                }
                if ("save".equals(name)) {
                    BloodRequestEntity r = (BloodRequestEntity) args[0];
                    r.setId((long) (savedRequests.size() + 1));
                    savedRequests.add(r);
                    return r;
                }
                return null;
            }
        );

        passwordEncoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "$2a$10$hashed_" + rawPassword;
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return encodedPassword.equals(encode(rawPassword));
            }
        };

        objectMapper = new ObjectMapper();

        demoDataLoader = new DemoDataLoader(
            userRepository,
            donorProfileRepository,
            bankRepository,
            bankInventoryRepository,
            bloodRequestRepository,
            passwordEncoder,
            objectMapper
        );
    }

    @Test
    void seedsDefaultAccountsAndDemoData() throws Exception {
        demoDataLoader.run();

        // Verifies default admin and staff and donor users were created
        assertThat(savedUsers).isNotEmpty();
        assertThat(savedUsers).anyMatch(u -> "admin".equals(u.getUsername()));
        assertThat(savedUsers).anyMatch(u -> "staff_central".equals(u.getUsername()));
        assertThat(savedUsers).anyMatch(u -> "donor_arjun".equals(u.getUsername()));

        // Verifies banks were seeded
        assertThat(savedBanks).isNotEmpty();

        // Verifies donors were seeded
        assertThat(savedProfiles).isNotEmpty();

        // Verifies sample requests were seeded
        assertThat(savedRequests).hasSize(2);
        assertThat(savedRequests).anyMatch(r -> r.getStatus() == RequestStatus.PENDING_REVIEW);
        assertThat(savedRequests).anyMatch(r -> r.getStatus() == RequestStatus.OPEN);
    }
}
