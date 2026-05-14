package com.example.invoiceflow;

import com.example.invoiceflow.stripe.StripeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StripeProperties.class)
public class InvoiceflowApplication {

	public static void main(String[] args) {
		SpringApplication.run(InvoiceflowApplication.class, args);
	}

}
