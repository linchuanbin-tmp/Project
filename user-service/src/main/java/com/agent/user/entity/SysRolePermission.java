package com.agent.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@TableName("sys_role_permission")
@NoArgsConstructor
@AllArgsConstructor
public class SysRolePermission {
    private Long roleId;
    private Long permId;
}
