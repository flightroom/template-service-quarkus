package dev.mfeorchestrator.service;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ServiceTest {

    @Test
    void helloReturnsAMessage() {
        given().when().get("/api/hello")
            .then().statusCode(200)
            .body("message", notNullValue());
    }

    @Test
    void livenessIsUp() {
        given().when().get("/q/health/live")
            .then().statusCode(200)
            .body("status", is("UP"))
            .body("checks.name", hasItem("alive"));
    }

    @Test
    void readinessIsUp() {
        given().when().get("/q/health/ready")
            .then().statusCode(200)
            .body("status", is("UP"))
            .body("checks.name", hasItem("ready"));
    }

    @Test
    void openApiJsonContainsHello() {
        given().when().get("/q/openapi.json")
            .then().statusCode(200)
            .contentType(containsString("application/json"))
            .body("openapi", containsString("3."))
            .body("paths", hasKey("/api/hello"));
    }

    @Test
    void swaggerUiIsServed() {
        given().when().get("/q/swagger-ui/")
            .then().statusCode(200)
            .body(containsString("<html"));
    }
}
