package tut.ac.za.AgriFinanceAPIs.ai;

record OllamaGenerateRequest(String model, String prompt, boolean stream, String system,
        java.util.Map<String, Object> options) {
}
