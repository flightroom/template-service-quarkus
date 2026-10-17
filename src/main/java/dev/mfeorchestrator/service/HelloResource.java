package dev.mfeorchestrator.service;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/hello")
@Tag(name = "hello")
public class HelloResource {

    public record HelloResponse(String message) {
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Example endpoint")
    public HelloResponse hello() {
        return new HelloResponse("Hello from %serviceSlug%");
    }
}
