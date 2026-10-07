package tut.ac.za.AgriFinanceAPIs.ai;

public record ChatResponse(String model, String response, java.util.List<Source> sources) {
    public record Source(String id, String title, String url, String publisher, String region,
            String checkedOn, String reviewStatus) {
        public Source(String id, String title) { this(id, title, null, null, null, null, null); }
    }
}
