package {{package}};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class {{mainAppName}} {
    public static void main(String[] args) {
        SpringApplication.run({{mainAppName}}.class, args);
    }
}