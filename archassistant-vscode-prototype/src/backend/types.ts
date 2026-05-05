export type Severity = 'INFO' | 'WARNING' | 'ERROR' | 'CRITICAL';
export type StrategyType = 'PRE' | 'POST' | 'HYBRID';
export type RuleType =
  | 'DEPENDENCY'
  | 'NAMING_CONVENTION'
  | 'ANNOTATION_CHECK'
  | 'LAYER_ISOLATION'
  | 'CYCLE_CHECK'
  | 'INHERITANCE_CHECK'
  | 'INTERFACE_CHECK'
  | 'MODIFIER_CHECK'
  | 'METHOD_SIGNATURE_CHECK'
  | 'FIELD_CHECK'
  | 'EXCEPTION_CHECK'
  | 'CUSTOM';

export type ConstraintType =
  | 'NO_DEPENDENCY'
  | 'MUST_DEPEND'
  | 'NAMING_SUFFIX'
  | 'NAMING_PREFIX'
  | 'HAS_ANNOTATION'
  | 'NO_ANNOTATION'
  | 'NO_CYCLE'
  | 'MAX_CYCLE_LENGTH'
  | 'SHOULD_EXTEND'
  | 'SHOULD_NOT_EXTEND'
  | 'SHOULD_IMPLEMENT'
  | 'SHOULD_NOT_IMPLEMENT'
  | 'SHOULD_BE_PUBLIC'
  | 'SHOULD_NOT_BE_PUBLIC'
  | 'SHOULD_BE_FINAL'
  | 'SHOULD_NOT_BE_FINAL'
  | 'SHOULD_BE_ABSTRACT'
  | 'SHOULD_NOT_BE_ABSTRACT'
  | 'RETURN_TYPE'
  | 'PARAMETER_COUNT'
  | 'PARAMETER_TYPES'
  | 'METHOD_VISIBILITY'
  | 'METHOD_NAME_PATTERN'
  | 'FIELD_TYPE'
  | 'FIELD_VISIBILITY'
  | 'FIELD_ANNOTATION'
  | 'FIELD_NAME_PATTERN'
  | 'SHOULD_ONLY_THROW'
  | 'SHOULD_NOT_THROW'
  | 'CUSTOM';

export type SelectorMode = 'PACKAGE' | 'CLASS_TYPE' | 'LAYER' | 'ANNOTATION' | 'MEMBER';
export type ClassType = 'CONTROLLER' | 'SERVICE' | 'REPOSITORY' | 'ENTITY' | 'DTO' | 'OTHER';
export type LayerType =
  | 'CONTROLLER'
  | 'SERVICE'
  | 'REPOSITORY'
  | 'ENTITY'
  | 'DTO'
  | 'DOMAIN'
  | 'APPLICATION'
  | 'INFRASTRUCTURE'
  | 'INTERFACE'
  | 'VIEW'
  | 'VIEWMODEL'
  | 'PORT'
  | 'ADAPTER'
  | 'API'
  | 'IMPL'
  | 'FEATURE'
  | 'COMMON'
  | 'OTHER';

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
  primaryProfile: string;
  confidence: number;
  scores: Record<string, number>;
  reasons: string[];
  candidateProfiles: string[];
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
    artifactKind?: 'CLASS' | 'INTERFACE' | 'RECORD' | 'MULTI_FILE' | null;
  } | null;
  rules?: string[] | null;
  collectMetrics?: boolean;
  expectedClassName?: string | null;
  classpath?: string | null;
}

export interface ValidationViolation {
  ruleId: string;
  description: string;
  className: string;
  lineNumber?: number | null;
  severity: Severity;
}

export interface ValidationResult {
  passed: boolean;
  violations: ValidationViolation[];
  message?: string | null;
  executionTimeMs: number;
}
