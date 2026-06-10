import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';

import {
  ApiService,
  ApplicationRequest,
  ApplicationStatus,
  CareerProfile,
  CareerStage,
  Dashboard,
  DegreeRequirement,
  DiscoverySort,
  DiscoveredJob,
  Job,
  JobApplication,
  JobProvider,
  JobRequest,
  OpportunityType,
  Resume,
  ResumeAnalysis,
  SavedSearch,
  SavedSearchRequest,
  SearchAlert,
  SponsorshipStatus,
  WorkplaceType
} from './core/api.service';
import { AuthService } from './core/auth.service';

type WorkspaceView = 'dashboard' | 'discover' | 'applications' | 'profile';

const EMPTY_PROFILE: CareerProfile = {
  phone: '',
  location: '',
  headline: '',
  education: '',
  graduationYear: null,
  yearsExperience: 0,
  desiredRoles: '',
  skills: '',
  linkedinUrl: '',
  portfolioUrl: '',
  updatedAt: null
};

function switchWorkplaceLabel(workplaceType: WorkplaceType): string {
  switch (workplaceType) {
    case 'ON_SITE':
      return 'On-site';
    case 'REMOTE':
      return 'Remote';
    case 'HYBRID':
      return 'Hybrid';
    default:
      return 'Workplace unspecified';
  }
}

/**
 * Root workspace coordinator for the first MVP.
 *
 * The app intentionally keeps its four small views together while their
 * workflows are still evolving. Once a view grows beyond basic CRUD, it can be
 * extracted into a routed feature component without changing ApiService.
 */
@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  readonly authMode = signal<'login' | 'register'>('login');
  readonly view = signal<WorkspaceView>('dashboard');
  readonly loading = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly dashboard = signal<Dashboard | null>(null);
  readonly jobs = signal<Job[]>([]);
  readonly applications = signal<JobApplication[]>([]);
  readonly resumes = signal<Resume[]>([]);
  readonly analyses = signal<Record<string, ResumeAnalysis>>({});
  readonly analyzingJobId = signal<string | null>(null);
  readonly discoveryResults = signal<DiscoveredJob[]>([]);
  readonly discoveryLoading = signal(false);
  readonly discoverySearched = signal(false);
  readonly hideAppliedDiscovery = signal(true);
  readonly savedSearches = signal<SavedSearch[]>([]);
  readonly searchAlerts = signal<SearchAlert[]>([]);
  readonly unreadAlertCount = computed(() =>
    this.searchAlerts().filter(alert => !alert.seen).length);
  readonly profileMode = signal<'view' | 'edit'>('view');
  readonly savedProfile = signal<CareerProfile>({ ...EMPTY_PROFILE });
  readonly visibleDiscoveryResults = computed(() =>
    this.discoveryResults().filter(job =>
      !this.hideAppliedDiscovery() || !this.hasAppliedToJob(job)));
  readonly hiddenAppliedCount = computed(() =>
    this.discoveryResults().length - this.visibleDiscoveryResults().length);
  readonly applicationStatuses: ApplicationStatus[] = [
    'SAVED',
    'APPLIED',
    'INTERVIEW',
    'OFFER',
    'REJECTED',
    'WITHDRAWN'
  ];
  readonly manualApplicationStatuses: {
    value: Exclude<ApplicationStatus, 'SAVED'>;
    label: string;
    description: string;
  }[] = [
    {
      value: 'APPLIED',
      label: 'Awaiting response',
      description: 'Applied, but have not heard back yet.'
    },
    {
      value: 'INTERVIEW',
      label: 'Interview',
      description: 'The company invited me to interview.'
    },
    {
      value: 'OFFER',
      label: 'Offer',
      description: 'I received an offer.'
    },
    {
      value: 'REJECTED',
      label: 'Declined',
      description: 'The company decided not to move forward.'
    },
    {
      value: 'WITHDRAWN',
      label: 'Withdrawn',
      description: 'I chose to withdraw my application.'
    }
  ];

  credentials = {
    email: '',
    password: '',
    firstName: '',
    lastName: ''
  };

  newJob: JobRequest = this.emptyJob();
  newApplication: ApplicationRequest = this.emptyApplication();
  profile: CareerProfile = { ...EMPTY_PROFILE };
  savedSearchName = '';
  discoverySearch: {
    provider: JobProvider | '';
    companyIdentifier: string;
    companyName: string;
    query: string;
    location: string;
    countryCode: string;
    workplaceType: WorkplaceType | '';
    postedWithinDays: number | null;
    sort: DiscoverySort;
    entryLevelOnly: boolean;
    opportunityType: OpportunityType | '';
    careerStage: CareerStage | '';
    degreeRequirement: DegreeRequirement | '';
    sponsorshipStatus: SponsorshipStatus | '';
    maximumExperience: number | null;
  } = {
    provider: '',
    companyIdentifier: '',
    companyName: '',
    query: '',
    location: '',
    countryCode: 'US',
    workplaceType: '',
    postedWithinDays: 30,
    sort: 'RELEVANCE',
    entryLevelOnly: true,
    opportunityType: '',
    careerStage: '',
    degreeRequirement: '',
    sponsorshipStatus: '',
    maximumExperience: 3
  };

  constructor(
    readonly auth: AuthService,
    private readonly api: ApiService
  ) {}

  ngOnInit(): void {
    if (this.auth.isAuthenticated()) {
      this.loadWorkspace();
    }
  }

  submitAuth(): void {
    this.beginRequest();
    const request = this.authMode() === 'login'
      ? this.auth.login(this.credentials.email, this.credentials.password)
      : this.auth.register(this.credentials);

    request.subscribe({
      next: () => {
        this.loading.set(false);
        this.loadWorkspace();
      },
      error: error => this.handleError(error)
    });
  }

  toggleAuthMode(): void {
    this.error.set('');
    this.authMode.update(mode => mode === 'login' ? 'register' : 'login');
  }

  selectView(view: WorkspaceView): void {
    this.view.set(view);
    this.error.set('');
    this.success.set('');
  }

  addJob(form: NgForm): void {
    if (form.invalid) {
      return;
    }
    this.beginRequest();
    this.api.createJob(this.newJob).subscribe({
      next: () => {
        this.newJob = this.emptyJob();
        form.resetForm(this.newJob);
        this.loading.set(false);
        this.success.set('Opportunity saved.');
        this.loadDashboardData();
      },
      error: error => this.handleError(error)
    });
  }

  deleteJob(id: string): void {
    this.api.deleteJob(id).subscribe({
      next: () => this.loadDashboardData(),
      error: error => this.handleError(error)
    });
  }

  analyzeJob(job: Job): void {
    const resume = this.resumes()[0];
    if (!resume) {
      this.error.set('Upload a resume before running an analysis.');
      this.selectView('profile');
      return;
    }
    this.error.set('');
    this.success.set('');
    this.analyzingJobId.set(job.id);
    this.api.analyzeResume(resume.id, job.id).subscribe({
      next: analysis => {
        this.analyses.update(items => ({ ...items, [job.id]: analysis }));
        this.analyzingJobId.set(null);
      },
      error: error => {
        this.analyzingJobId.set(null);
        this.handleError(error);
      }
    });
  }

  searchDiscovery(form: NgForm): void {
    if (form.invalid) {
      return;
    }
    this.executeDiscoverySearch();
  }

  saveCurrentSearch(): void {
    const name = this.savedSearchName.trim();
    if (!name) {
      this.error.set('Give this search a short name first.');
      return;
    }
    const request: SavedSearchRequest = {
      name,
      query: this.discoverySearch.query,
      location: this.discoverySearch.location,
      countryCode: this.discoverySearch.countryCode,
      workplaceType: this.discoverySearch.workplaceType || null,
      postedWithinDays: this.discoverySearch.postedWithinDays,
      entryLevelOnly: this.discoverySearch.entryLevelOnly,
      opportunityType: this.discoverySearch.opportunityType || null,
      careerStage: this.discoverySearch.careerStage || null,
      degreeRequirement: this.discoverySearch.degreeRequirement || null,
      sponsorshipStatus: this.discoverySearch.sponsorshipStatus || null,
      maximumExperience: this.discoverySearch.maximumExperience,
      alertsEnabled: true
    };
    this.beginRequest();
    this.api.createSavedSearch(request).subscribe({
      next: saved => {
        this.savedSearches.update(searches => [saved, ...searches]);
        this.savedSearchName = '';
        this.loading.set(false);
        this.success.set(`Saved search "${saved.name}". New matches will appear here.`);
      },
      error: error => this.handleError(error)
    });
  }

  runSavedSearch(search: SavedSearch): void {
    this.discoverySearch = {
      ...this.discoverySearch,
      provider: '',
      companyIdentifier: '',
      companyName: '',
      query: search.query ?? '',
      location: search.location ?? '',
      countryCode: search.countryCode ?? 'US',
      workplaceType: search.workplaceType ?? '',
      postedWithinDays: search.postedWithinDays,
      sort: 'RELEVANCE',
      entryLevelOnly: search.entryLevelOnly,
      opportunityType: search.opportunityType ?? '',
      careerStage: search.careerStage ?? '',
      degreeRequirement: search.degreeRequirement ?? '',
      sponsorshipStatus: search.sponsorshipStatus ?? '',
      maximumExperience: search.maximumExperience
    };
    this.executeDiscoverySearch();
  }

  deleteSavedSearch(id: string): void {
    this.api.deleteSavedSearch(id).subscribe({
      next: () => {
        this.savedSearches.update(searches => searches.filter(search => search.id !== id));
        this.searchAlerts.update(alerts => alerts.filter(alert => alert.savedSearchId !== id));
      },
      error: error => this.handleError(error)
    });
  }

  markAlertsSeen(): void {
    this.api.markSearchAlertsSeen().subscribe({
      next: () => this.searchAlerts.update(alerts =>
        alerts.map(alert => ({ ...alert, seen: true }))),
      error: error => this.handleError(error)
    });
  }

  providerLabel(provider: JobProvider): string {
    switch (provider) {
      case 'GREENHOUSE':
        return 'Greenhouse';
      case 'LEVER':
        return 'Lever';
      case 'USAJOBS':
        return 'USAJOBS';
      case 'ADZUNA':
        return 'Adzuna';
    }
  }

  private executeDiscoverySearch(): void {
    this.error.set('');
    this.success.set('');
    this.discoveryLoading.set(true);
    this.discoverySearched.set(false);
    this.api.discoverJobs(this.discoverySearch).subscribe({
      next: jobs => {
        this.discoveryResults.set(jobs);
        this.discoveryLoading.set(false);
        this.discoverySearched.set(true);
        if (jobs.length === 0) {
          this.success.set('No matching public postings were found. Try broader role terms or fewer filters.');
        }
      },
      error: error => {
        this.discoveryLoading.set(false);
        this.discoverySearched.set(true);
        this.handleError(error);
      }
    });
  }

  saveDiscoveredJob(job: DiscoveredJob): void {
    this.beginRequest();
    this.api.createJob({
      company: job.company,
      title: job.title,
      location: job.location ?? '',
      description: job.description,
      sourceUrl: job.sourceUrl,
      experienceMin: job.experienceMin,
      experienceMax: job.experienceMax,
      publishedAt: job.publishedAt
    }).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(`${job.title} saved to your shortlist.`);
        this.loadDashboardData();
      },
      error: error => this.handleError(error)
    });
  }

  isDiscoveredJobSaved(job: DiscoveredJob): boolean {
    return this.jobs().some(saved => saved.sourceUrl === job.sourceUrl);
  }

  hasAppliedToJob(job: DiscoveredJob): boolean {
    return this.applications().some(application =>
      application.status !== 'SAVED' && this.sameOpportunity(
        application.company,
        application.title,
        application.sourceUrl,
        job.company,
        job.title,
        job.sourceUrl));
  }

  addApplication(form: NgForm): void {
    if (form.invalid) {
      return;
    }
    this.beginRequest();
    this.api.createApplication(this.newApplication).subscribe({
      next: () => {
        this.newApplication = this.emptyApplication();
        form.resetForm(this.newApplication);
        this.loading.set(false);
        this.success.set('Application added to your pipeline.');
        this.loadApplicationData();
      },
      error: error => this.handleError(error)
    });
  }

  updateApplicationStatus(application: JobApplication, status: ApplicationStatus): void {
    const request = this.applicationRequestFrom(application, status);
    this.api.updateApplication(application.id, request).subscribe({
      next: updated => {
        this.applications.update(items =>
          items.map(item => item.id === updated.id ? updated : item));
        this.loadDashboardSummary();
      },
      error: error => this.handleError(error)
    });
  }

  deleteApplication(id: string): void {
    this.api.deleteApplication(id).subscribe({
      next: () => this.loadApplicationData(),
      error: error => this.handleError(error)
    });
  }

  saveProfile(form: NgForm): void {
    if (form.invalid) {
      return;
    }
    this.beginRequest();
    this.api.updateProfile(this.profile).subscribe({
      next: profile => {
        const editable = this.editableProfile(profile);
        this.profile = { ...editable };
        this.savedProfile.set(editable);
        this.profileMode.set('view');
        this.loading.set(false);
        this.success.set('Career profile updated.');
      },
      error: error => this.handleError(error)
    });
  }

  uploadResume(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.beginRequest();
    this.api.uploadResume(file).subscribe({
      next: () => {
        input.value = '';
        this.loading.set(false);
        this.success.set('Resume uploaded.');
        this.loadResumes();
      },
      error: error => {
        input.value = '';
        this.handleError(error);
      }
    });
  }

  deleteResume(id: string): void {
    this.api.deleteResume(id).subscribe({
      next: () => this.loadResumes(),
      error: error => this.handleError(error)
    });
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024 * 1024) {
      return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  statusLabel(status: ApplicationStatus): string {
    switch (status) {
      case 'APPLIED':
        return 'Awaiting response';
      case 'INTERVIEW':
        return 'Interview';
      case 'OFFER':
        return 'Offer';
      case 'REJECTED':
        return 'Declined';
      case 'WITHDRAWN':
        return 'Withdrawn';
      default:
        return 'Saved';
    }
  }

  workplaceLabel(workplaceType: WorkplaceType): string {
    return switchWorkplaceLabel(workplaceType);
  }

  opportunityLabel(type: OpportunityType): string {
    switch (type) {
      case 'FULL_TIME':
        return 'Full-time';
      case 'PART_TIME':
        return 'Part-time';
      case 'INTERNSHIP':
        return 'Internship';
      case 'APPRENTICESHIP':
        return 'Apprenticeship';
      case 'CONTRACT':
        return 'Contract';
      default:
        return 'Type unspecified';
    }
  }

  careerStageLabel(stage: CareerStage): string {
    switch (stage) {
      case 'NEW_GRAD':
        return 'New grad';
      case 'ENTRY_LEVEL':
        return 'Entry-level';
      case 'EARLY_CAREER':
        return 'Early-career likely';
      case 'INTERNSHIP':
        return 'Internship';
      case 'APPRENTICESHIP':
        return 'Apprenticeship';
      default:
        return 'Career stage unspecified';
    }
  }

  degreeLabel(requirement: DegreeRequirement): string {
    switch (requirement) {
      case 'NO_DEGREE_REQUIRED':
        return 'Degree not required';
      case 'DEGREE_PREFERRED':
        return 'Degree preferred';
      case 'BACHELORS_REQUIRED':
        return "Bachelor's required";
      case 'ADVANCED_DEGREE_REQUIRED':
        return 'Advanced degree required';
      default:
        return 'Degree not stated';
    }
  }

  sponsorshipLabel(status: SponsorshipStatus): string {
    switch (status) {
      case 'AVAILABLE':
        return 'Sponsorship available';
      case 'NOT_AVAILABLE':
        return 'No sponsorship';
      default:
        return 'Sponsorship not stated';
    }
  }

  countryLabel(countryCode: string | null): string {
    return countryCode === 'US' ? 'United States' : countryCode ?? 'Country unspecified';
  }

  startProfileEdit(): void {
    this.profile = { ...this.savedProfile() };
    this.profileMode.set('edit');
  }

  cancelProfileEdit(): void {
    this.profile = { ...this.savedProfile() };
    this.profileMode.set('view');
  }

  hasProfileDetails(profile: CareerProfile = this.savedProfile()): boolean {
    return Boolean(
      profile.headline
      || profile.location
      || profile.phone
      || profile.education
      || profile.graduationYear
      || profile.desiredRoles
      || profile.skills
      || profile.linkedinUrl
      || profile.portfolioUrl);
  }

  splitProfileList(value: string | null): string[] {
    return value?.split(/[,;\n]/)
      .map(item => item.trim())
      .filter(Boolean) ?? [];
  }

  profileInitials(): string {
    const first = this.auth.session()?.firstName?.charAt(0) ?? '';
    const last = this.auth.session()?.lastName?.charAt(0) ?? '';
    return `${first}${last}`.toUpperCase() || 'JS';
  }

  logout(): void {
    this.auth.logout();
    this.dashboard.set(null);
    this.jobs.set([]);
    this.applications.set([]);
    this.resumes.set([]);
    this.analyses.set({});
    this.discoveryResults.set([]);
    this.discoverySearched.set(false);
    this.savedProfile.set({ ...EMPTY_PROFILE });
    this.savedSearches.set([]);
    this.searchAlerts.set([]);
    this.profile = { ...EMPTY_PROFILE };
    this.profileMode.set('view');
    this.view.set('dashboard');
  }

  private loadWorkspace(): void {
    this.loadDashboardData();
    this.loadApplicationData();
    this.api.getProfile().subscribe({
      next: profile => {
        const editable = this.editableProfile(profile);
        this.profile = { ...editable };
        this.savedProfile.set(editable);
        this.profileMode.set(this.hasProfileDetails(editable) ? 'view' : 'edit');
      },
      error: error => this.handleError(error)
    });
    this.loadResumes();
    this.loadSavedSearchData();
  }

  private loadDashboardData(): void {
    this.loadDashboardSummary();
    this.api.getJobs().subscribe({
      next: jobs => this.jobs.set(jobs),
      error: error => this.handleError(error)
    });
  }

  private loadDashboardSummary(): void {
    this.api.getDashboard().subscribe({
      next: dashboard => this.dashboard.set(dashboard),
      error: error => this.handleError(error)
    });
  }

  private loadApplicationData(): void {
    this.api.getApplications().subscribe({
      next: applications => {
        this.applications.set(applications);
        this.loadDashboardSummary();
      },
      error: error => this.handleError(error)
    });
  }

  private loadResumes(): void {
    this.api.getResumes().subscribe({
      next: resumes => this.resumes.set(resumes),
      error: error => this.handleError(error)
    });
  }

  private loadSavedSearchData(): void {
    this.api.getSavedSearches().subscribe({
      next: searches => this.savedSearches.set(searches),
      error: error => this.handleError(error)
    });
    this.api.getSearchAlerts().subscribe({
      next: alerts => this.searchAlerts.set(alerts),
      error: error => this.handleError(error)
    });
  }

  private applicationRequestFrom(
    application: JobApplication,
    status: ApplicationStatus
  ): ApplicationRequest {
    return {
      jobPostingId: application.jobPostingId,
      company: application.company,
      title: application.title,
      sourceUrl: application.sourceUrl ?? '',
      status,
      appliedAt: application.appliedAt,
      notes: application.notes ?? ''
    };
  }

  private editableProfile(profile: CareerProfile): CareerProfile {
    return {
      ...EMPTY_PROFILE,
      ...profile
    };
  }

  private sameOpportunity(
    firstCompany: string,
    firstTitle: string,
    firstUrl: string | null,
    secondCompany: string,
    secondTitle: string,
    secondUrl: string | null
  ): boolean {
    const firstNormalizedUrl = this.normalizeUrl(firstUrl);
    const secondNormalizedUrl = this.normalizeUrl(secondUrl);
    if (firstNormalizedUrl && secondNormalizedUrl) {
      return firstNormalizedUrl === secondNormalizedUrl;
    }
    return this.normalizeText(firstCompany) === this.normalizeText(secondCompany)
      && this.normalizeText(firstTitle) === this.normalizeText(secondTitle);
  }

  private normalizeUrl(value: string | null): string {
    return value?.trim().toLowerCase().replace(/\/+$/, '') ?? '';
  }

  private normalizeText(value: string): string {
    return value.toLowerCase().replace(/\s+/g, ' ').trim();
  }

  private emptyJob(): JobRequest {
    return {
      company: '',
      title: '',
      location: '',
      description: '',
      sourceUrl: '',
      experienceMin: 0,
      experienceMax: 3,
      publishedAt: null
    };
  }

  private emptyApplication(): ApplicationRequest {
    return {
      jobPostingId: null,
      company: '',
      title: '',
      sourceUrl: '',
      status: 'APPLIED',
      appliedAt: new Date().toISOString().slice(0, 10),
      notes: ''
    };
  }

  private beginRequest(): void {
    this.loading.set(true);
    this.error.set('');
    this.success.set('');
  }

  private handleError(error: HttpErrorResponse): void {
    this.loading.set(false);
    this.error.set(error.error?.detail ?? 'Something went wrong. Please try again.');
  }
}
