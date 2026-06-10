import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface Dashboard {
  savedJobs: number;
  totalApplications: number;
  applicationsByStatus: Record<string, number>;
}

export interface Job {
  id: string;
  company: string;
  title: string;
  location: string | null;
  description: string | null;
  sourceUrl: string | null;
  experienceMin: number | null;
  experienceMax: number | null;
  publishedAt: string | null;
  createdAt: string;
}

export interface JobRequest {
  company: string;
  title: string;
  location: string;
  description: string;
  sourceUrl: string;
  experienceMin: number | null;
  experienceMax: number | null;
  publishedAt: string | null;
}

export type ApplicationStatus =
  | 'SAVED'
  | 'APPLIED'
  | 'INTERVIEW'
  | 'OFFER'
  | 'REJECTED'
  | 'WITHDRAWN';

export interface JobApplication {
  id: string;
  jobPostingId: string | null;
  company: string;
  title: string;
  sourceUrl: string | null;
  status: ApplicationStatus;
  appliedAt: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ApplicationRequest {
  jobPostingId: string | null;
  company: string;
  title: string;
  sourceUrl: string;
  status: ApplicationStatus;
  appliedAt: string | null;
  notes: string;
}

export interface CareerProfile {
  phone: string | null;
  location: string | null;
  headline: string | null;
  education: string | null;
  graduationYear: number | null;
  yearsExperience: number | null;
  desiredRoles: string | null;
  skills: string | null;
  linkedinUrl: string | null;
  portfolioUrl: string | null;
  updatedAt: string | null;
}

export interface Resume {
  id: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface ResumeAnalysis {
  resumeId: string;
  jobPostingId: string;
  overallScore: number;
  keywordScore: number;
  experienceScore: number;
  structureScore: number;
  matchedSkills: string[];
  missingSkills: string[];
  suggestions: string[];
}

export type JobProvider = 'GREENHOUSE' | 'LEVER' | 'USAJOBS' | 'ADZUNA';
export type DiscoverySort = 'RELEVANCE' | 'NEWEST';
export type WorkplaceType = 'REMOTE' | 'HYBRID' | 'ON_SITE' | 'UNKNOWN';

export interface DiscoveredJob {
  externalId: string;
  provider: JobProvider;
  company: string;
  title: string;
  location: string | null;
  countryCode: string | null;
  workplaceType: WorkplaceType;
  description: string;
  sourceUrl: string;
  publishedAt: string | null;
  expiresAt: string | null;
  experienceMin: number | null;
  experienceMax: number | null;
  entryLevelLikely: boolean;
}

export interface SavedSearch {
  id: string;
  name: string;
  query: string | null;
  location: string | null;
  countryCode: string | null;
  workplaceType: WorkplaceType | null;
  postedWithinDays: number | null;
  entryLevelOnly: boolean;
  alertsEnabled: boolean;
  lastCheckedAt: string | null;
  createdAt: string;
}

export interface SavedSearchRequest {
  name: string;
  query: string;
  location: string;
  countryCode: string;
  workplaceType: WorkplaceType | null;
  postedWithinDays: number | null;
  entryLevelOnly: boolean;
  alertsEnabled: boolean;
}

export interface SearchAlert {
  id: string;
  savedSearchId: string;
  savedSearchName: string;
  job: DiscoveredJob;
  discoveredAt: string;
  seen: boolean;
}

/**
 * Typed boundary for every backend call used by the Angular application.
 *
 * Keeping URLs and response shapes here makes components responsible for user
 * interaction only, and gives future API changes one predictable edit point.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private readonly http: HttpClient) {}

  getDashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>('/api/dashboard');
  }

  getJobs(): Observable<Job[]> {
    return this.http.get<Job[]>('/api/jobs');
  }

  createJob(request: JobRequest): Observable<Job> {
    return this.http.post<Job>('/api/jobs', request);
  }

  deleteJob(id: string): Observable<void> {
    return this.http.delete<void>(`/api/jobs/${id}`);
  }

  getApplications(): Observable<JobApplication[]> {
    return this.http.get<JobApplication[]>('/api/applications');
  }

  createApplication(request: ApplicationRequest): Observable<JobApplication> {
    return this.http.post<JobApplication>('/api/applications', request);
  }

  updateApplication(id: string, request: ApplicationRequest): Observable<JobApplication> {
    return this.http.put<JobApplication>(`/api/applications/${id}`, request);
  }

  deleteApplication(id: string): Observable<void> {
    return this.http.delete<void>(`/api/applications/${id}`);
  }

  getProfile(): Observable<CareerProfile> {
    return this.http.get<CareerProfile>('/api/profile');
  }

  updateProfile(profile: CareerProfile): Observable<CareerProfile> {
    return this.http.put<CareerProfile>('/api/profile', profile);
  }

  getResumes(): Observable<Resume[]> {
    return this.http.get<Resume[]>('/api/profile/resumes');
  }

  uploadResume(file: File): Observable<Resume> {
    const data = new FormData();
    data.append('file', file);
    return this.http.post<Resume>('/api/profile/resumes', data);
  }

  deleteResume(id: string): Observable<void> {
    return this.http.delete<void>(`/api/profile/resumes/${id}`);
  }

  analyzeResume(resumeId: string, jobPostingId: string): Observable<ResumeAnalysis> {
    return this.http.post<ResumeAnalysis>('/api/resume-analysis', {
      resumeId,
      jobPostingId
    });
  }

  discoverJobs(search: {
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
  }): Observable<DiscoveredJob[]> {
    let params = new HttpParams()
      .set('entryLevelOnly', search.entryLevelOnly)
      .set('sort', search.sort);
    if (search.provider) {
      params = params.set('provider', search.provider);
    }
    if (search.companyIdentifier.trim()) {
      params = params.set('companyIdentifier', search.companyIdentifier.trim());
    }
    if (search.companyName.trim()) {
      params = params.set('companyName', search.companyName.trim());
    }
    if (search.query.trim()) {
      params = params.set('query', search.query.trim());
    }
    if (search.location.trim()) {
      params = params.set('location', search.location.trim());
    }
    params = params.set('countryCode', search.countryCode);
    if (search.workplaceType) {
      params = params.set('workplaceType', search.workplaceType);
    }
    if (search.postedWithinDays !== null) {
      params = params.set('postedWithinDays', search.postedWithinDays);
    }
    return this.http.get<DiscoveredJob[]>('/api/discovery', { params });
  }

  getSavedSearches(): Observable<SavedSearch[]> {
    return this.http.get<SavedSearch[]>('/api/saved-searches');
  }

  createSavedSearch(request: SavedSearchRequest): Observable<SavedSearch> {
    return this.http.post<SavedSearch>('/api/saved-searches', request);
  }

  deleteSavedSearch(id: string): Observable<void> {
    return this.http.delete<void>(`/api/saved-searches/${id}`);
  }

  getSearchAlerts(): Observable<SearchAlert[]> {
    return this.http.get<SearchAlert[]>('/api/saved-searches/alerts');
  }

  markSearchAlertsSeen(): Observable<void> {
    return this.http.post<void>('/api/saved-searches/alerts/seen', {});
  }
}
