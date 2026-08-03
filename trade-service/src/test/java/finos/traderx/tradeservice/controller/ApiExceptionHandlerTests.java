package finos.traderx.tradeservice.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTests {
  private static final String OVERSELL_MESSAGE =
      "You cannot sell more Treasury face amount than you own and have available.";

  @Test
  void serializesConflictReasonAsDetail() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RejectingController())
        .setControllerAdvice(new ApiExceptionHandler())
        .build();

    mockMvc.perform(get("/test/oversell"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.detail").value(OVERSELL_MESSAGE));
  }

  @RestController
  static class RejectingController {
    @GetMapping("/test/oversell")
    void rejectOversell() {
      throw new ResponseStatusException(HttpStatus.CONFLICT, OVERSELL_MESSAGE);
    }
  }
}
