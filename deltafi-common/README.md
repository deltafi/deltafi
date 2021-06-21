Sample ZipkinRestClient quarkus usage:

```
@Singleton
public class ZipkinConfig {

    @Inject
    HttpClient httpClient;

    @ConfigProperty(name = "zipkin.url")
    String zipkinUrl;

    @Produces
    @ApplicationScoped
    public ZipkinService zipkinService() {
        return new ZipkinService(httpClient, zipkinUrl);
    }
}
```

