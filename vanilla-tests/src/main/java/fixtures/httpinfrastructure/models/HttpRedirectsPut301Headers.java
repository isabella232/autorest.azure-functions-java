package fixtures.httpinfrastructure.models;

import com.azure.core.annotation.Fluent;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The HttpRedirectsPut301Headers model.
 */
@Fluent
public final class HttpRedirectsPut301Headers {
    /*
     * The Location property.
     */
    @JsonProperty(value = "Location")
    private String location;

    /**
     * Creates an instance of HttpRedirectsPut301Headers class.
     */
    public HttpRedirectsPut301Headers() {
        location = "/http/failure/500";
    }

    /**
     * Get the location property: The Location property.
     * 
     * @return the location value.
     */
    public String getLocation() {
        return this.location;
    }

    /**
     * Set the location property: The Location property.
     * 
     * @param location the location value to set.
     * @return the HttpRedirectsPut301Headers object itself.
     */
    public HttpRedirectsPut301Headers setLocation(String location) {
        this.location = location;
        return this;
    }

    public void validate() {
    }
}
