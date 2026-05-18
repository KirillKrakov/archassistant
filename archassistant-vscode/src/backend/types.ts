export enum Severity {
  INFO = 'INFO',
  WARNING = 'WARNING',
  ERROR = 'ERROR',
  CRITICAL = 'CRITICAL'
}

export enum StrategyType {
  PRE = 'PRE',
  POST = 'POST',
  HYBRID = 'HYBRID'
}

export enum ArtifactKind {
  CLASS = 'CLASS',
  INTERFACE = 'INTERFACE',
  RECORD = 'RECORD',
  ENUM = 'ENUM',
  MULTI_FILE = 'MULTI_FILE'
}

export enum RuleType {
  DEPENDENCY = 'DEPENDENCY',
  NAMING_CONVENTION = 'NAMING_CONVENTION',
  ANNOTATION_CHECK = 'ANNOTATION_CHECK',
  LAYER_ISOLATION = 'LAYER_ISOLATION',
  CYCLE_CHECK = 'CYCLE_CHECK',
  INHERITANCE_CHECK = 'INHERITANCE_CHECK',
  INTERFACE_CHECK = 'INTERFACE_CHECK',
  MODIFIER_CHECK = 'MODIFIER_CHECK',
  METHOD_SIGNATURE_CHECK = 'METHOD_SIGNATURE_CHECK',
  FIELD_CHECK = 'FIELD_CHECK',
  EXCEPTION_CHECK = 'EXCEPTION_CHECK',
  CUSTOM = 'CUSTOM'
}

export enum ConstraintType {
  NO_DEPENDENCY = 'NO_DEPENDENCY',
  MUST_DEPEND = 'MUST_DEPEND',
  NAMING_SUFFIX = 'NAMING_SUFFIX',
  NAMING_PREFIX = 'NAMING_PREFIX',
  HAS_ANNOTATION = 'HAS_ANNOTATION',
  NO_ANNOTATION = 'NO_ANNOTATION',
  NO_CYCLE = 'NO_CYCLE',
  MAX_CYCLE_LENGTH = 'MAX_CYCLE_LENGTH',
  SHOULD_EXTEND = 'SHOULD_EXTEND',
  SHOULD_NOT_EXTEND = 'SHOULD_NOT_EXTEND',
  SHOULD_IMPLEMENT = 'SHOULD_IMPLEMENT',
  SHOULD_NOT_IMPLEMENT = 'SHOULD_NOT_IMPLEMENT',
  SHOULD_BE_PUBLIC = 'SHOULD_BE_PUBLIC',
  SHOULD_NOT_BE_PUBLIC = 'SHOULD_NOT_BE_PUBLIC',
  SHOULD_BE_FINAL = 'SHOULD_BE_FINAL',
  SHOULD_NOT_BE_FINAL = 'SHOULD_NOT_BE_FINAL',
  SHOULD_BE_ABSTRACT = 'SHOULD_BE_ABSTRACT',
  SHOULD_NOT_BE_ABSTRACT = 'SHOULD_NOT_BE_ABSTRACT',
  RETURN_TYPE = 'RETURN_TYPE',
  PARAMETER_COUNT = 'PARAMETER_COUNT',
  PARAMETER_TYPES = 'PARAMETER_TYPES',
  METHOD_VISIBILITY = 'METHOD_VISIBILITY',
  METHOD_NAME_PATTERN = 'METHOD_NAME_PATTERN',
  FIELD_TYPE = 'FIELD_TYPE',
  FIELD_VISIBILITY = 'FIELD_VISIBILITY',
  FIELD_ANNOTATION = 'FIELD_ANNOTATION',
  FIELD_NAME_PATTERN = 'FIELD_NAME_PATTERN',
  SHOULD_ONLY_THROW = 'SHOULD_ONLY_THROW',
  SHOULD_NOT_THROW = 'SHOULD_NOT_THROW',
  CUSTOM = 'CUSTOM'
}

export enum SelectorMode {
  PACKAGE = 'PACKAGE',
  CLASS_TYPE = 'CLASS_TYPE',
  LAYER = 'LAYER',
  ANNOTATION = 'ANNOTATION',
  MEMBER = 'MEMBER'
}

export enum ClassType {
  CONTROLLER = 'CONTROLLER',
  SERVICE = 'SERVICE',
  REPOSITORY = 'REPOSITORY',
  ENTITY = 'ENTITY',
  DTO = 'DTO',
  OTHER = 'OTHER'
}

export enum LayerType {
  CONTROLLER = 'CONTROLLER',
  SERVICE = 'SERVICE',
  REPOSITORY = 'REPOSITORY',
  ENTITY = 'ENTITY',
  DTO = 'DTO',
  DOMAIN = 'DOMAIN',
  APPLICATION = 'APPLICATION',
  INFRASTRUCTURE = 'INFRASTRUCTURE',
  INTERFACE = 'INTERFACE',
  VIEW = 'VIEW',
  VIEWMODEL = 'VIEWMODEL',
  PORT = 'PORT',
  ADAPTER = 'ADAPTER',
  API = 'API',
  IMPL = 'IMPL',
  FEATURE = 'FEATURE',
  COMMON = 'COMMON',
  OTHER = 'OTHER'
}

export enum ProjectProfile {
  SPRING_LAYERED = 'SPRING_LAYERED',
  SPRING_FEATURED = 'SPRING_FEATURED',
  CLEAN = 'CLEAN',
  HEXAGONAL = 'HEXAGONAL',
  MVVM = 'MVVM',
  MODULAR = 'MODULAR',
  UNKNOWN = 'UNKNOWN'
}

export enum ExportFormat {
  CSV = 'CSV',
  JSON = 'JSON',
  JSON_PRETTY = 'JSON_PRETTY'
}

export interface ArchitecturalRule {
  id: string;
  name: string;
  description?: string | null;
  type: RuleType;
  from_package: string;
  to_package?: string | null;
  to_packages?: string[] | null;
  constraint: ConstraintType;
  pattern?: string | null;
  annotation?: string | null;
  from_selector_mode?: SelectorMode;
  to_selector_mode?: SelectorMode;
  from_class_type?: ClassType | null;
  to_class_type?: ClassType | null;
  from_layer_type?: LayerType | null;
  to_layer_type?: LayerType | null;
  from_name_pattern?: string | null;
  to_name_pattern?: string | null;
  from_method_name_pattern?: string | null;
  to_method_name_pattern?: string | null;
  from_field_name_pattern?: string | null;
  to_field_name_pattern?: string | null;
  from_return_type?: string | null;
  to_return_type?: string | null;
  from_parameter_types?: string[] | null;
  to_parameter_types?: string[] | null;
  from_throws_types?: string[] | null;
  to_throws_types?: string[] | null;
  from_modifiers?: string[] | null;
  to_modifiers?: string[] | null;
  from_field_type?: string | null;
  to_field_type?: string | null;
  slice_pattern?: string | null;
  max_cycle_length?: number | null;
  severity: Severity;
  weight: number;
  enabled: boolean;
  suggested: boolean;
}

export interface RuleSettings {
  max_iterations: number;
  timeout_seconds: number;
  default_strategy: string;
  fail_on_critical: boolean;
  auto_fix_naming: boolean;
}

export interface RulesConfig {
  version?: string | null;
  project_id: string;
  project_type?: string | null;
  rules: ArchitecturalRule[];
  settings?: RuleSettings | null;
  project_path?: string | null;
  created_at?: string | null;
  updated_at?: string | null;
}

export interface ProjectProfileDetection {
  primaryProfile: ProjectProfile | string;
  confidence: number;
  scores: Record<string, number>;
  reasons: string[];
  candidateProfiles: (ProjectProfile | string)[];
  isConfident: boolean;
}

export interface WorkspaceModuleSuggestions {
  moduleId: string;
  moduleRoot: string;
  profile: ProjectProfileDetection;
  rules: ArchitecturalRule[];
}

export interface HealthResponse {
  status: string;
  timestamp?: string;
  [key: string]: unknown;
}

export interface BackendSuccessResponse {
  success: boolean;
  projectId?: string;
  projectPath?: string;
  error?: string;
}

export interface GeneratedFile {
  packageName: string;
  className: string;
  code: string;
  artifactKind: ArtifactKind;
}

export interface ValidationViolation {
  ruleId: string;
  description: string;
  className: string;
  lineNumber?: number | null;
  severity: Severity;
}

export interface ComplianceScore {
  total: number;
  rulesPass: number;
  patternMatch: number;
  dependencyCorrect: number;
  weights: {
    rulesPass: number;
    patternMatch: number;
    dependencyCorrect: number;
  };
  violations: ValidationViolation[];
  calculatedAt?: string;
}

export interface GenerationData {
  code: string;
  files?: GeneratedFile[];
  score?: ComplianceScore;
  strategy: StrategyType;
  iterations: number;
  warnings: string[];
  suggestions: string[];
}

export interface CodeGenerationRequest {
  prompt: string;
  projectId: string;
  strategy?: StrategyType;
  maxIterations?: number;
  context?: {
    targetPackage?: string | null;
    existingTypes?: string[];
    codeSnippet?: string | null;
    module?: string | null;
    artifactKind?: ArtifactKind | null;
  } | null;
  rules?: string[] | null;
  collectMetrics?: boolean;
  expectedClassName?: string | null;
  classpath?: string | null;
}

export interface CodeGenerationResponse {
  success: boolean;
  data?: GenerationData;
  error?: { code: string; message: string; details?: Record<string, unknown> } | null;
  metadata: {
    generationTimeMs: number;
    validationTimeMs: number;
    totalTimeMs: number;
    timestamp: string;
    model?: string | null;
  };
}

export interface ProjectMetrics {
  projectId: string;
  totalGenerations: number;
  successfulGenerations?: number;
  failedGenerations?: number;
  avgScore: number | null;
  avgIterations?: number | null;
  avgGenerationTimeMs?: number | null;
  avgValidationTimeMs?: number | null;
  avgTotalTimeMs?: number | null;
  successRate?: number | null;
  successRatePercent?: number | null;
  lastGeneration: string | null;
  recentHistory: GenerationHistoryItem[];
}

export interface GenerationHistoryItem {
  id?: string;
  strategy: string;
  score: number | null;
  iterations: number;
  success: boolean;
  timestamp: string;
  generationTimeMs?: number;
  validationTimeMs?: number;
  totalTimeMs?: number;
  violationsCount?: number;
  prompt?: string | null;
  generatedCode?: string | null;
}

export interface ComparisonResult {
  projectId: string | null;
  strategies: Record<string, StrategyComparison>;
  recommendation: Recommendation | null;
  comparedAt: string;
}

export interface StrategyComparison {
  strategy: string;
  totalGenerations: number;
  successRate: number;
  avgScore: number | null;
  avgIterations: number;
  avgGenerationTimeMs: number;
  avgValidationTimeMs: number;
  avgTotalTimeMs?: number | null;
  avgViolations: number;
}

export interface ClearMetricsResponse {
  success: boolean;
  projectId: string;
  deletedCount: number;
}

export interface Recommendation {
  bestStrategy: string;
  reason: string;
  confidence: number;
}

export interface ExportRequest {
  projectId?: string;
  strategy?: string;
  fromDate?: string;
  toDate?: string;
  format: ExportFormat;
  includeViolations: boolean;
}

export interface ValidationResult {
  passed: boolean;
  violations: ValidationViolation[];
  message?: string | null;
  executionTimeMs: number;
}