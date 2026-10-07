package tut.ac.za.AgriFinanceAPIs.ai;

public record ChatResponse(String model, String response, java.util.List<Source> sources) {
    public record Source(String id, String title) {}
}
