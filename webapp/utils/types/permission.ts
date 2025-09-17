export const PermissionConstant = {
    READ_SERIES: 'read:series',
    WRITE_SERIES: 'write:series',
    READ_CAMPAIGN: 'read:campaign',
    WRITE_CAMPAIGN: 'write:campaign',
    ABORT_CAMPAIGN: 'abort:campaign',
    CREATE_CAMPAIGN: 'create:campaign',
    READ_REPORT: 'read:report',
    WRITE_REPORT: 'write:report'
} as const;

export type PermissionEnum = typeof PermissionConstant[keyof typeof PermissionConstant];
