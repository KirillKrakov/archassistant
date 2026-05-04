export interface HealthResponse {
  status: string;
  version?: string;
  timestamp?: string;
}

export interface RulesConfig {
  version?: string;
  project_id: string;
  project_type?: string;
  rules: ArchitecturalRule[];
  settings?: RuleSettings;
  project_path?: string;
  created_at?: string;
  updated_at?: string;
}

export interface RuleSettings {
  max_iterations?: number;
  timeout_seconds?: number;
  default_strategy?: string;
  fail_on_critical?: boolean;
  auto_fix_naming?: boolean;
}

export interface ArchitecturalRule {
  id: string;
  name: string;
  description?: string;
  type: RuleType;
  from_package: string;
  to_package?: string;
  to_packages?: string[];
  constraint: ConstraintType;
  pattern?: string;
  annotation?: string;
  from_selector_mode?: SelectorMode;
  to_selector_mode?: SelectorMode;
  from_class_type?: ClassType;
  to_class_type?: ClassType;
  from_layer_type?: LayerType;
  to_layer_type?: LayerType;
  from_name_pattern?: string;
  to_name_pattern?: string;
  from_method_name_pattern?: string;
  to_method_name_pattern?: string;
  from_field_name_pattern?: string;
  to_field_name_pattern?: string;
  from_return_type?: string;
  to_return_type?: string;
  from_parameter_types?: string[];
  to_parameter_types?: string[];
  from_throws_types?: string[];
  to_throws_types?: string[];
  from_modifiers?: string[];
  to_modifiers?: string[];
  from_field_type?: string;
  to_field_type?: string;
  slice_pattern?: string;
  max_cycle_length?: number;
  severity: Severity;
  weight: number;
  enabled: boolean;
  suggested: boolean;
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

export enum Severity {
  INFO = 'INFO',
  WARNING = 'WARNING',
  ERROR = 'ERROR',
  CRITICAL = 'CRITICAL'
}

export interface WorkspaceModuleSuggestions {
  moduleId: string;
  moduleRoot: string;
  profile: ProjectProfileDetection;
  rules: ArchitecturalRule[];
}

export interface ProjectProfileDetection {
  primaryProfile: ProjectProfile;
  confidence: number;
  scores: Record<string, number>;
  reasons: string[];
  candidateProfiles: ProjectProfile[];
  isConfident: boolean;
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

export interface CodeGenerationRequest {
  prompt: string;
  projectId: string;
  strategy: StrategyType;
  maxIterations?: number;
  context?: GenerationContext;
  rules?: string[];
  collectMetrics?: boolean;
  expectedClassName?: string;
  classpath?: string;
}

export enum StrategyType {
  PRE = 'PRE',
  POST = 'POST',
  HYBRID = 'HYBRID'
}

export interface GenerationContext {
  targetPackage?: string;
  existingTypes?: string[];
  codeSnippet?: string;
  module?: string;
  artifactKind?: ArtifactKind;
}

export enum ArtifactKind {
  CLASS = 'CLASS',
  INTERFACE = 'INTERFACE',
  RECORD = 'RECORD',
  ENUM = 'ENUM',
  MULTI_FILE = 'MULTI_FILE'
}

export interface CodeGenerationResponse {
  success: boolean;
  data?: GenerationData;
  error?: ErrorDetails;
  metadata: ResponseMetadata;
}

export interface GeneratedFile {
  packageName: string;
  className: string;
  code: string;
  artifactKind: ArtifactKind;
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

export interface ComplianceScore {
  total: number;
  rulesPass: number;
  patternMatch: number;
  dependencyCorrect: number;
  weights: ScoreWeights;
  violations: Violation[];
  calculatedAt?: string;
}

export interface ScoreWeights {
  rulesPass: number;
  patternMatch: number;
  dependencyCorrect: number;
}

export interface Violation {
  ruleId: string;
  description: string;
  className: string;
  lineNumber?: number | null;
  severity: Severity;
}

export interface ErrorDetails {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

export interface ResponseMetadata {
  generationTimeMs: number;
  validationTimeMs: number;
  totalTimeMs: number;
  timestamp: string;
  model?: string | null;
}

export interface ProjectMetrics {
  projectId: string;
  totalGenerations: number;
  avgScore: number | null;
  lastGeneration: string | null;
  recentHistory: GenerationHistoryItem[];
}

export interface GenerationHistoryItem {
  strategy: string;
  score: number | null;
  iterations: number;
  success: boolean;
  timestamp: string;
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
  avgViolations: number;
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

export enum ExportFormat {
  CSV = 'CSV',
  JSON = 'JSON',
  JSON_PRETTY = 'JSON_PRETTY'
}

export interface ValidationRequest {
  code: string;
  className?: string;
  projectId?: string;
  classpath?: string;
  rules?: RuleDefinition[];
}

export interface RuleDefinition {
  id?: string;
  name?: string;
  description?: string;
  type: string;
  from_package: string;
  to_package?: string;
  to_packages?: string[];
  constraint?: string;
  from_selector_mode?: string;
  to_selector_mode?: string;
  from_class_type?: string;
  to_class_type?: string;
  from_layer_type?: string;
  to_layer_type?: string;
  from_name_pattern?: string;
  to_name_pattern?: string;
  from_method_name_pattern?: string;
  to_method_name_pattern?: string;
  from_field_name_pattern?: string;
  to_field_name_pattern?: string;
  from_return_type?: string;
  to_return_type?: string;
  from_parameter_types?: string[];
  to_parameter_types?: string[];
  from_throws_types?: string[];
  to_throws_types?: string[];
  from_modifiers?: string[];
  to_modifiers?: string[];
  from_field_type?: string;
  to_field_type?: string;
  pattern?: string;
  annotation?: string;
  slice_pattern?: string;
  max_cycle_length?: number;
  severity?: string;
  weight?: number;
  enabled: boolean;
}

export interface ValidationResult {
  passed: boolean;
  violations: Violation[];
  message?: string;
  executionTimeMs: number;
}

export interface ValidationResponse {
  result: ValidationResult;
}