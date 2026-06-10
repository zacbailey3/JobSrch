import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';

import {
  ApiService,
  ApplicationRequest,
  ApplicationStatus,
  CareerProfile,
  Dashboard,
  DiscoveredJob,
  Job,
  JobApplication,
  JobProvider,
  JobRequest,
  Resume,
  ResumeAnalysis
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

  credentials = {
    email: '',
    password: '',
    firstName: '',
    lastName: ''
  };

  newJob: JobRequest = this.emptyJob();
  newApplication: ApplicationRequest = this.emptyApplication();
  profile: CareerProfile = { ...EMPTY_PROFILE };
  discoverySearch: {
    provider: JobProvider | '';
    companyIdentifier: string;
    companyName: string;
    query: string;
    location: string;
    entryLevelOnly: boolean;
  } = {
    provider: '',
    companyIdentifier: '',
    companyName: '',
    query: '',
    location: '',
    entryLevelOnly: true
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
    return status.charAt(0) + status.slice(1).toLowerCase();
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
