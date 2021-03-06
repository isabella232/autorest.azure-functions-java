package fixtures.httpinfrastructure.models;

import com.azure.core.annotation.Fluent;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The HttpRedirectsHead301Headers model.
 */
@Fluent
public final class HttpRedirectsHead301Headers {
    /*
     * The Location property.
     */
    @JsonProperty(value = "Location")
    private String location;

    /**
     * Creates an instance of HttpRedirectsHead301Headers class.
     */
    public HttpRedirectsHead301Headers() {
        location = "/http/success/head/200";
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
     * @return the HttpRedirectsHead301Headers object itself.
     */
    public HttpRedirectsHead301Headers setLocation(String location) {
        this.location = location;
        return this;
    }

    public void validate() {
    }
}
