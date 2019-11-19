package com.sap.cloud.security.javasec.samples.usage;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.sap.cloud.security.javasec.test.JwtGenerator;
import com.sap.cloud.security.javasec.test.RSAKeypair;
import com.sap.cloud.security.token.Token;
import com.sap.cloud.security.xsuaa.http.HttpHeaders;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.junit.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

public class HelloJavaServletTest {

	private static final int APPLICATION_SERVER_PORT = 8282;
	private static final int MOCK_TOKEN_KEY_SERVICE_PORT = 33195;

	private static Properties oldProperties;

	@Rule
	public final WireMockRule wireMockRule = new WireMockRule(options().port(MOCK_TOKEN_KEY_SERVICE_PORT));

	@Rule
	public final TomcatTestServer server = new TomcatTestServer(APPLICATION_SERVER_PORT, "src/test/webapp");

	@Rule
	public final RSAKeypair keyPair = new RSAKeypair();

	private Token validToken;

	@Before
	public void setUp() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		validToken = createValidToken();
	}

	@BeforeClass
	public static void prepareTest() throws Exception {
		oldProperties = System.getProperties();
		System.setProperty("VCAP_SERVICES", IOUtils.resourceToString("/vcap.json", StandardCharsets.UTF_8));
	}

	@AfterClass
	public static void restoreProperties() {
		System.setProperties(oldProperties);
	}

	@Test
	public void requestWithoutToken_statusUnauthorized() throws IOException {
		HttpGet request = createGetRequest("Bearer ");
		try (CloseableHttpResponse response = HttpClients.createDefault().execute(request)) {
			assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_UNAUTHORIZED);
		}
	}

	@Test
	public void requestWithoutHeader_statusUnauthorized() throws Exception {
		HttpGet request = createGetRequest("Bearer " + validToken.getAccessToken());
		request.setHeader(HttpHeaders.AUTHORIZATION, null);
		try (CloseableHttpResponse response = HttpClients.createDefault().execute(request)) {
			assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_UNAUTHORIZED);
		}
	}

	@Test
	public void request_withValidToken() throws IOException {
		HttpGet request = createGetRequest("Bearer " + validToken.getAccessToken());

		wireMockRule.stubFor(get(urlEqualTo("/")).willReturn(aResponse().withBody(createTokenKeyResponse())));

		try (CloseableHttpResponse response = HttpClients.createDefault().execute(request)) {
			assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_OK);
		}
	}

	private Token createValidToken() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		return new JwtGenerator(keyPair.getPrivate())
				.withAlgorithm("RS256")
				.withHeaderParameter("jku", "http://localhost:" + MOCK_TOKEN_KEY_SERVICE_PORT)
				.withClaim("cid", "sb-clientId!20")
				.createToken();
	}

	private String createTokenKeyResponse() throws IOException {
		return IOUtils.resourceToString("/token_keys_template.json", StandardCharsets.UTF_8)
				.replace("$kid", "default-kid")
				.replace("$public_key", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
	}

	private HttpGet createGetRequest(String bearer_token) {
		HttpGet httpPost = new HttpGet("http://localhost:" + APPLICATION_SERVER_PORT + "/hello-java-security");
		httpPost.setHeader(HttpHeaders.AUTHORIZATION, bearer_token);
		return httpPost;
	}

}