package tut.ac.za.AgriFinanceAPIs.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(@NotBlank @Size(max = 2000) String prompt, @Size(max = 6000) String context) {
}
