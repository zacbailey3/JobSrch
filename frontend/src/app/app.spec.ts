import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { App } from './app';
import { DiscoveredJob, Job, JobApplication } from './core/api.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient()],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the product name', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('JobSrch');
  });

  it('shows the password recovery form from sign in', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    app.showForgotPassword();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Reset your password');
    expect(compiled.querySelector('[name="resetEmail"]')).not.toBeNull();
  });

  it('starts discovery with US and recent-job defaults', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    expect(app.discoverySearch.countryCode).toBe('US');
    expect(app.discoverySearch.postedWithinDays).toBe(30);
    expect(app.discoverySearch.sort).toBe('RELEVANCE');
    expect(app.discoverySearch.maximumExperience).toBe(3);
  });

  it('keeps the discovery form focused on non-redundant filters', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const app = fixture.componentInstance;
    app.auth.session.set({
      expiresIn: 3600,
      userId: 'test-user',
      email: 'student@example.com',
      firstName: 'Student',
      lastName: 'Developer'
    });
    app.view.set('discover');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('[name="careerStage"]')).toBeNull();
    expect(compiled.querySelector('[name="degreeRequirement"]')).toBeNull();
    expect(compiled.querySelector('[name="sponsorshipStatus"]')).toBeNull();
    expect(compiled.querySelector('[name="companyIdentifier"]')).toBeNull();
    expect(compiled.querySelector('[name="maximumExperience"]')).not.toBeNull();
  });

  it('starts a manual application as applied and awaiting response', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    expect(app.newApplication.status).toBe('APPLIED');
    expect(app.newApplication.appliedAt).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(app.statusLabel('APPLIED')).toBe('Awaiting response');
    expect(app.manualApplicationStatuses.map(status => status.value))
      .toEqual(['APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN']);
  });

  it('prefills the application pipeline from a saved job', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const job: Job = {
      id: 'saved-job-1',
      company: 'Example Company',
      title: 'Junior Developer',
      location: 'Remote',
      description: 'Build software',
      sourceUrl: 'https://jobs.example.com/junior-developer',
      experienceMin: 0,
      experienceMax: 2,
      publishedAt: null,
      createdAt: '2026-06-09T00:00:00Z'
    };

    app.trackSavedJob(job);

    expect(app.view()).toBe('applications');
    expect(app.newApplication).toEqual({
      jobPostingId: job.id,
      company: job.company,
      title: job.title,
      sourceUrl: job.sourceUrl,
      status: 'APPLIED',
      appliedAt: app.newApplication.appliedAt,
      notes: ''
    });
    expect(app.newApplication.appliedAt).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(app.success()).toContain('Confirm its date and current stage');
  });

  it('recognizes a saved job that is already in the application pipeline', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const job: Job = {
      id: 'saved-job-1',
      company: 'Example Company',
      title: 'Junior Developer',
      location: null,
      description: null,
      sourceUrl: 'https://jobs.example.com/junior-developer',
      experienceMin: null,
      experienceMax: null,
      publishedAt: null,
      createdAt: '2026-06-09T00:00:00Z'
    };
    const application: JobApplication = {
      id: 'application-1',
      jobPostingId: job.id,
      company: job.company,
      title: job.title,
      sourceUrl: job.sourceUrl,
      status: 'INTERVIEW',
      appliedAt: '2026-06-09',
      notes: null,
      createdAt: '2026-06-09T00:00:00Z',
      updatedAt: '2026-06-10T00:00:00Z'
    };

    app.applications.set([application]);

    expect(app.applicationForSavedJob(job)).toBe(application);
    app.trackSavedJob(job);
    expect(app.view()).toBe('applications');
    expect(app.success()).toContain('already tracked as Interview');
  });

  it('renders the manual application fields and outcome choices', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const app = fixture.componentInstance;
    app.auth.session.set({
      expiresIn: 3600,
      userId: 'test-user',
      email: 'student@example.com',
      firstName: 'Student',
      lastName: 'Developer'
    });
    app.view.set('applications');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const dateInput = compiled.querySelector<HTMLInputElement>(
      'input[name="appliedAt"]');
    const urlInput = compiled.querySelector<HTMLInputElement>(
      'input[name="applicationUrl"]');

    expect(compiled.textContent).toContain('Record an application');
    expect(compiled.textContent).toContain('Have you heard from them?');
    expect(compiled.querySelectorAll('input[name="applicationStatus"]')).toHaveLength(5);
    expect(dateInput?.required).toBe(true);
    expect(urlInput?.required).toBe(false);
  });

  it('hides discovered roles already recorded as applied', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const discovered: DiscoveredJob = {
      externalId: 'job-1',
      provider: 'LEVER',
      company: 'Example',
      title: 'Junior Developer',
      location: 'Remote',
      countryCode: 'US',
      workplaceType: 'REMOTE',
      description: 'Build software',
      sourceUrl: 'https://jobs.example.com/job-1',
      publishedAt: null,
      expiresAt: null,
      experienceMin: 0,
      experienceMax: 2,
      entryLevelLikely: true,
      opportunityType: 'FULL_TIME',
      careerStage: 'ENTRY_LEVEL',
      degreeRequirement: 'NOT_STATED',
      sponsorshipStatus: 'NOT_STATED',
      verifiedAt: '2026-06-09T00:00:00Z',
      matchReasons: ['The title explicitly uses a junior or entry-level label.'],
      cautions: ['Visa sponsorship is not specified.']
    };
    const application: JobApplication = {
      id: 'application-1',
      jobPostingId: null,
      company: 'Example',
      title: 'Junior Developer',
      sourceUrl: 'https://jobs.example.com/job-1/',
      status: 'APPLIED',
      appliedAt: '2026-06-09',
      notes: null,
      createdAt: '2026-06-09T00:00:00Z',
      updatedAt: '2026-06-09T00:00:00Z'
    };

    app.discoveryResults.set([discovered]);
    app.applications.set([application]);

    expect(app.visibleDiscoveryResults()).toHaveLength(0);
    app.hideAppliedDiscovery.set(false);
    expect(app.visibleDiscoveryResults()).toEqual([discovered]);
  });

  it('restores saved profile details when editing is cancelled', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.savedProfile.set({
      phone: null,
      location: 'Chicago',
      headline: 'New graduate developer',
      education: 'Computer Science',
      graduationYear: 2026,
      yearsExperience: 1,
      desiredRoles: 'Backend developer',
      skills: 'Java, SQL',
      linkedinUrl: null,
      portfolioUrl: null,
      updatedAt: null
    });

    app.startProfileEdit();
    app.profile.headline = 'Unsaved change';
    app.cancelProfileEdit();

    expect(app.profileMode()).toBe('view');
    expect(app.profile.headline).toBe('New graduate developer');
  });
});
