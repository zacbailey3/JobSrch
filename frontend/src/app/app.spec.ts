import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { App } from './app';
import { ApplicationsPage } from './applications/applications-page';
import { AuthPage } from './auth/auth-page';
import { DiscoveredJob, Job, JobApplication } from './core/api.service';
import { DiscoveryPage } from './discovery/discovery-page';
import { WorkspaceStore } from './workspace/workspace.store';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])]
    }).compileComponents();
  });

  it('creates the router host', () => {
    expect(TestBed.createComponent(App).componentInstance).toBeTruthy();
  });
});

describe('AuthPage', () => {
  const router = {
    navigate: vi.fn().mockResolvedValue(true),
    navigateByUrl: vi.fn().mockResolvedValue(true)
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap({}) }
          }
        }
      ]
    }).compileComponents();
    router.navigate.mockClear();
    router.navigateByUrl.mockClear();
  });

  it('renders the product name', () => {
    const fixture = TestBed.createComponent(AuthPage);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('JobSrch');
  });

  it('shows the password recovery form from sign in', () => {
    const fixture = TestBed.createComponent(AuthPage);
    fixture.componentInstance.showForgotPassword();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Reset your password');
    expect(compiled.querySelector('[name="resetEmail"]')).not.toBeNull();
  });
});

describe('WorkspaceStore', () => {
  const router = {
    navigate: vi.fn().mockResolvedValue(true)
  };
  let workspace: WorkspaceStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router }
      ]
    });
    workspace = TestBed.inject(WorkspaceStore);
    router.navigate.mockClear();
  });

  it('starts discovery with US and recent-job defaults', () => {
    expect(workspace.discoverySearch.countryCode).toBe('US');
    expect(workspace.discoverySearch.postedWithinDays).toBe(30);
    expect(workspace.discoverySearch.sort).toBe('RELEVANCE');
    expect(workspace.discoverySearch.maximumExperience).toBe(3);
  });

  it('starts a manual application as applied and awaiting response', () => {
    expect(workspace.newApplication.status).toBe('APPLIED');
    expect(workspace.newApplication.appliedAt).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(workspace.statusLabel('APPLIED')).toBe('Awaiting response');
    expect(workspace.manualApplicationStatuses.map(status => status.value))
      .toEqual(['APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN']);
  });

  it('prefills the application pipeline from a saved job', () => {
    const job: Job = savedJob();

    workspace.trackSavedJob(job);

    expect(router.navigate).toHaveBeenCalledWith(['/', 'applications']);
    expect(workspace.newApplication).toEqual({
      jobPostingId: job.id,
      company: job.company,
      title: job.title,
      sourceUrl: job.sourceUrl,
      status: 'APPLIED',
      appliedAt: workspace.newApplication.appliedAt,
      notes: ''
    });
    expect(workspace.success()).toContain('Confirm its date and current stage');
  });

  it('recognizes a saved job already in the application pipeline', () => {
    const job = savedJob();
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
    workspace.applications.set([application]);

    expect(workspace.applicationForSavedJob(job)).toBe(application);
    workspace.trackSavedJob(job);
    expect(workspace.success()).toContain('already tracked as Interview');
  });

  it('hides discovered roles already recorded as applied', () => {
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
    workspace.discoveryResults.set([discovered]);
    workspace.applications.set([{
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
    }]);

    expect(workspace.visibleDiscoveryResults()).toHaveLength(0);
    workspace.hideAppliedDiscovery.set(false);
    expect(workspace.visibleDiscoveryResults()).toEqual([discovered]);
  });

  it('restores saved profile details when editing is cancelled', () => {
    workspace.savedProfile.set({
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

    workspace.startProfileEdit();
    workspace.profile.headline = 'Unsaved change';
    workspace.cancelProfileEdit();

    expect(workspace.profileMode()).toBe('view');
    expect(workspace.profile.headline).toBe('New graduate developer');
  });
});

describe('Routed workspace pages', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DiscoveryPage, ApplicationsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
  });

  it('keeps discovery focused on non-redundant filters', () => {
    const fixture = TestBed.createComponent(DiscoveryPage);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[name="careerStage"]')).toBeNull();
    expect(compiled.querySelector('[name="degreeRequirement"]')).toBeNull();
    expect(compiled.querySelector('[name="sponsorshipStatus"]')).toBeNull();
    expect(compiled.querySelector('[name="companyIdentifier"]')).toBeNull();
    expect(compiled.querySelector('[name="maximumExperience"]')).not.toBeNull();
  });

  it('renders the manual application fields and outcome choices', () => {
    const fixture = TestBed.createComponent(ApplicationsPage);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const dateInput = compiled.querySelector<HTMLInputElement>('input[name="appliedAt"]');
    const urlInput = compiled.querySelector<HTMLInputElement>('input[name="applicationUrl"]');

    expect(compiled.textContent).toContain('Record an application');
    expect(compiled.textContent).toContain('Have you heard from them?');
    expect(compiled.querySelectorAll('input[name="applicationStatus"]')).toHaveLength(5);
    expect(dateInput?.required).toBe(true);
    expect(urlInput?.required).toBe(false);
  });
});

function savedJob(): Job {
  return {
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
}
