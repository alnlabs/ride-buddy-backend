package com.alnlabs.ridebuddy.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profiles")
public class ProfileEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "home_lat")
    private Double homeLat;

    @Column(name = "home_lng")
    private Double homeLng;

    @Column(name = "home_label")
    private String homeLabel;

    @Column(name = "home_area_slug")
    private String homeAreaSlug;

    @Column(name = "office_lat")
    private Double officeLat;

    @Column(name = "office_lng")
    private Double officeLng;

    @Column(name = "office_label")
    private String officeLabel;

    @Column(name = "office_area_slug")
    private String officeAreaSlug;

    @Column(name = "experience_bio")
    private String experienceBio;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(name = "can_offer_rides", nullable = false)
    private boolean canOfferRides;

    @Column(name = "profile_strength", nullable = false)
    private int profileStrength;

    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;

    @Column(name = "job_role")
    private String jobRole;

    @Column(name = "company")
    private String company;

    @Column(name = "office_email")
    private String officeEmail;

    @Column(name = "office_email_verified", nullable = false)
    private boolean officeEmailVerified;

    @Column(name = "office_email_pending_code")
    private String officeEmailPendingCode;

    @Column(name = "office_email_code_expires_at")
    private Instant officeEmailCodeExpiresAt;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public Double getHomeLat() { return homeLat; }
    public void setHomeLat(Double homeLat) { this.homeLat = homeLat; }
    public Double getHomeLng() { return homeLng; }
    public void setHomeLng(Double homeLng) { this.homeLng = homeLng; }
    public String getHomeLabel() { return homeLabel; }
    public void setHomeLabel(String homeLabel) { this.homeLabel = homeLabel; }
    public String getHomeAreaSlug() { return homeAreaSlug; }
    public void setHomeAreaSlug(String homeAreaSlug) { this.homeAreaSlug = homeAreaSlug; }
    public Double getOfficeLat() { return officeLat; }
    public void setOfficeLat(Double officeLat) { this.officeLat = officeLat; }
    public Double getOfficeLng() { return officeLng; }
    public void setOfficeLng(Double officeLng) { this.officeLng = officeLng; }
    public String getOfficeLabel() { return officeLabel; }
    public void setOfficeLabel(String officeLabel) { this.officeLabel = officeLabel; }
    public String getOfficeAreaSlug() { return officeAreaSlug; }
    public void setOfficeAreaSlug(String officeAreaSlug) { this.officeAreaSlug = officeAreaSlug; }
    public String getExperienceBio() { return experienceBio; }
    public void setExperienceBio(String experienceBio) { this.experienceBio = experienceBio; }
    public Integer getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(Integer yearsExperience) { this.yearsExperience = yearsExperience; }
    public boolean isCanOfferRides() { return canOfferRides; }
    public void setCanOfferRides(boolean canOfferRides) { this.canOfferRides = canOfferRides; }
    public int getProfileStrength() { return profileStrength; }
    public void setProfileStrength(int profileStrength) { this.profileStrength = profileStrength; }
    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }
    public String getJobRole() { return jobRole; }
    public void setJobRole(String jobRole) { this.jobRole = jobRole; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getOfficeEmail() { return officeEmail; }
    public void setOfficeEmail(String officeEmail) { this.officeEmail = officeEmail; }
    public boolean isOfficeEmailVerified() { return officeEmailVerified; }
    public void setOfficeEmailVerified(boolean officeEmailVerified) { this.officeEmailVerified = officeEmailVerified; }
    public String getOfficeEmailPendingCode() { return officeEmailPendingCode; }
    public void setOfficeEmailPendingCode(String officeEmailPendingCode) { this.officeEmailPendingCode = officeEmailPendingCode; }
    public Instant getOfficeEmailCodeExpiresAt() { return officeEmailCodeExpiresAt; }
    public void setOfficeEmailCodeExpiresAt(Instant officeEmailCodeExpiresAt) { this.officeEmailCodeExpiresAt = officeEmailCodeExpiresAt; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
}
