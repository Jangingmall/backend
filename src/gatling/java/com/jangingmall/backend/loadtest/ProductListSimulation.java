package com.jangingmall.backend.loadtest;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class ProductListSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http
        .baseUrl("http://localhost:8080")
        .acceptHeader("application/json");

    ScenarioBuilder scn = scenario("상품 목록 조회")
        .exec(http("GET /api/products")
            .get("/api/products?size=20")
            .check(status().is(200)));

    {
        setUp(scn.injectOpen(
            rampUsers(20).during(10),
            constantUsersPerSec(20).during(30)
        )).protocols(httpProtocol)
          .assertions(
              global().responseTime().percentile(95).lt(2000),
              global().successfulRequests().percent().gt(99.0)
          );
    }
}
