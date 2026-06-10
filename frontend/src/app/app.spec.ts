import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { App } from './app';
import { DiscoveredJob, JobApplication } from './core/api.service';

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

  it('starts discovery with US and recent-job defaults', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    expect(app.discoverySearch.countryCode).toBe('US');
    expect(app.discoverySearch.postedWithinDays).toBe(30);
    expect(app.discoverySearch.sort).toBe('RELEVANCE');
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
      entryLevelLikely: true
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
