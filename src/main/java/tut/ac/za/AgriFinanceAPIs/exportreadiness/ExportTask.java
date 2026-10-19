package tut.ac.za.AgriFinanceAPIs.exportreadiness;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "export_tasks", uniqueConstraints =
        @UniqueConstraint(name = "uq_export_task_key", columnNames = {"application_id", "task_key"}))
public class ExportTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "requirement_id")
    private Long requirementId;

    @Column(name = "task_key", nullable = false, length = 120)
    private String taskKey;

    @Column(name = "stage_number", nullable = false)
    private int stageNumber;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "status_recorded")
    private Boolean statusRecorded;

    @Column(length = 2000)
    private String notes;

    protected ExportTask() { }

    public ExportTask(Long applicationId, Long requirementId, String taskKey, int stageNumber, String title) {
        this.applicationId = applicationId;
        this.requirementId = requirementId;
        this.taskKey = taskKey;
        this.stageNumber = stageNumber;
        this.title = title;
        this.status = "NOT_STARTED";
    }

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public Long getRequirementId() { return requirementId; }
    public String getTaskKey() { return taskKey; }
    public int getStageNumber() { return stageNumber; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isStatusRecorded() { return Boolean.TRUE.equals(statusRecorded); }
    public void setStatusRecorded(boolean statusRecorded) { this.statusRecorded = statusRecorded; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
