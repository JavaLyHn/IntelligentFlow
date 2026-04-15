import React, { useMemo, useState, memo } from 'react';
import {
  FlowSelect,
  FLowCollapse,
  FlowInput,
  FlowInputNumber,
  FlowTemplateEditor,
} from '@/components/workflow/ui';
import { Tooltip, Tag } from 'antd';
import { useTranslation } from 'react-i18next';
import { useMemoizedFn } from 'ahooks';
import { v4 as uuid } from 'uuid';
import { cloneDeep } from 'lodash';
import Inputs from '../components/inputs';
import Outputs from '../components/outputs';
import ExceptionHandling from '../components/exception-handling';
import { useNodeCommon } from '@/components/workflow/hooks/use-node-common';
import { Icons } from '@/components/workflow/icons';

interface MultiAgentProps {
  data: MultiAgentNodeData;
}

interface MultiAgentDetailProps {
  id: string;
  data: MultiAgentNodeData;
  nodeParam: MultiAgentNodeParam;
}

interface MultiAgentNodeData {
  nodeParam: MultiAgentNodeParam;
}

interface MultiAgentNodeParam {
  collaborationMode?: string;
  query?: string;
  maxIterations?: number;
  agents?: AgentConfig[];
}

interface AgentConfig {
  role: string;
  name: string;
  systemPrompt: string;
  capabilities?: string[];
}

const COLLABORATION_MODES = [
  {
    value: 'supervisor',
    label: 'Supervisor 主管模式',
    description: '主管Agent拆解任务并分发给专家Agent，汇总结果',
  },
  {
    value: 'pipeline',
    label: 'Pipeline 流水线模式',
    description: 'Agent按顺序依次执行，前一个输出作为后一个输入',
  },
  {
    value: 'swarm',
    label: 'Swarm 群智模式',
    description: '多个Agent并行探索不同方向，由评判Agent综合评判',
  },
];

const AGENT_ROLES = [
  { value: 'supervisor', label: 'Supervisor 主管', color: '#722ED1' },
  { value: 'searcher', label: 'Searcher 搜索专家', color: '#1890FF' },
  { value: 'analyzer', label: 'Analyzer 分析专家', color: '#13C2C2' },
  { value: 'writer', label: 'Writer 写作专家', color: '#52C41A' },
  { value: 'judge', label: 'Judge 评判专家', color: '#FA8C16' },
];

export const MultiAgent = memo(({ data }: MultiAgentProps) => {
  const modeConfig = COLLABORATION_MODES.find(
    m => m.value === data?.nodeParam?.collaborationMode
  );

  return (
    <>
      <div className="text-[#333] text-right">协作模式</div>
      <span>{modeConfig?.label || 'Supervisor 主管模式'}</span>
    </>
  );
});

const CollaborationModeSection = ({
  data,
  handleChangeNodeParam,
}): React.ReactElement => {
  const { t } = useTranslation();

  return (
    <FLowCollapse
      label={
        <div className="flex items-center justify-between">
          <div className="text-base font-medium flex items-center gap-1">
            <span>协作模式</span>
            <Tooltip
              title="选择多Agent协作的工作模式：Supervisor主管分发、Pipeline流水线串行、Swarm群智并行"
              overlayClassName="black-tooltip"
            >
              <img src={Icons.agent.questionMark} width={12} alt="" />
            </Tooltip>
          </div>
        </div>
      }
      content={
        <div className="rounded-md px-[18px] pb-3 pointer-events-auto">
          <FlowSelect
            value={data?.nodeParam?.collaborationMode || 'supervisor'}
            onChange={value =>
              handleChangeNodeParam(
                (data: MultiAgentNodeData, value: string) =>
                  (data.nodeParam.collaborationMode = value),
                value
              )
            }
          >
            {COLLABORATION_MODES.map(mode => (
              <FlowSelect.Option key={mode.value} value={mode.value}>
                <div className="flex items-center gap-1">
                  <div className="text-xs">{mode.label}</div>
                  <Tooltip
                    title={mode.description}
                    overlayClassName="black-tooltip"
                  >
                    <img src={Icons.agent.questionMark} width={12} alt="" />
                  </Tooltip>
                </div>
              </FlowSelect.Option>
            ))}
          </FlowSelect>
          <div className="mt-2 text-xs text-[#999]">
            {COLLABORATION_MODES.find(
              m => m.value === (data?.nodeParam?.collaborationMode || 'supervisor')
            )?.description}
          </div>
        </div>
      }
    />
  );
};

const QuerySection = ({
  id,
  data,
  handleChangeNodeParam,
}): React.ReactElement => {
  const { t } = useTranslation();
  const currentStore = useFlowsManager(state => state.getCurrentStore());
  const delayCheckNode = currentStore(state => state.delayCheckNode);

  return (
    <FLowCollapse
      label={
        <div className="flex items-center justify-between">
          <h4 className="text-base font-medium">
            <span className="text-[#F74E43] text-lg font-medium h-5">*</span>
            任务查询
          </h4>
        </div>
      }
      content={
        <div className="rounded-md px-[18px] pb-3 pointer-events-auto">
          <FlowTemplateEditor
            id={id}
            data={data}
            onBlur={() => delayCheckNode(id)}
            value={data?.nodeParam?.query}
            onChange={value =>
              handleChangeNodeParam(
                (data: MultiAgentNodeData, value: string) =>
                  (data.nodeParam.query = value),
                value
              )
            }
            placeholder="输入需要多Agent协作完成的任务描述..."
          />
        </div>
      }
    />
  );
};

const AgentListSection = ({
  data,
  handleChangeNodeParam,
}): React.ReactElement => {
  const { t } = useTranslation();
  const canvasesDisabled = useFlowsManager(state => state.canvasesDisabled);

  const agents: AgentConfig[] = useMemo(() => {
    return data?.nodeParam?.agents || [];
  }, [data?.nodeParam?.agents]);

  const handleAddAgent = useMemoizedFn(() => {
    const newAgents = [
      ...agents,
      {
        role: 'searcher',
        name: `Agent-${agents.length + 1}`,
        systemPrompt: '',
        capabilities: [],
      },
    ];
    handleChangeNodeParam(
      (data: MultiAgentNodeData, value: AgentConfig[]) =>
        (data.nodeParam.agents = value),
      newAgents
    );
  });

  const handleRemoveAgent = useMemoizedFn((index: number) => {
    const newAgents = agents.filter((_, i) => i !== index);
    handleChangeNodeParam(
      (data: MultiAgentNodeData, value: AgentConfig[]) =>
        (data.nodeParam.agents = value),
      newAgents
    );
  });

  const handleAgentChange = useMemoizedFn(
    (index: number, field: string, value: string | string[]) => {
      const newAgents = [...agents];
      newAgents[index] = { ...newAgents[index], [field]: value };
      handleChangeNodeParam(
        (data: MultiAgentNodeData, value: AgentConfig[]) =>
          (data.nodeParam.agents = value),
        newAgents
      );
    }
  );

  return (
    <FLowCollapse
      label={
        <div className="flex items-center justify-between">
          <div className="text-base font-medium flex items-center gap-1">
            <span>参与角色</span>
            <Tooltip
              title="配置参与协作的Agent角色。Supervisor模式需要1个主管+多个专家；Pipeline模式按顺序执行；Swarm模式需要1个评判+多个Worker"
              overlayClassName="black-tooltip"
            >
              <img src={Icons.agent.questionMark} width={12} alt="" />
            </Tooltip>
          </div>
          {!canvasesDisabled && (
            <div
              className="text-[#275EFF] text-xs font-medium mt-1 inline-flex items-center cursor-pointer gap-1.5 pl-6"
              onClick={e => {
                e.stopPropagation();
                handleAddAgent();
              }}
            >
              <img src={Icons.agent.inputAddIcon} className="w-3 h-3" alt="" />
              <span>添加角色</span>
            </div>
          )}
        </div>
      }
      content={
        <div className="rounded-md px-[18px] pb-3 pointer-events-auto flex flex-col gap-2">
          {agents.length === 0 && (
            <div className="text-xs text-[#999] py-2">
              未配置自定义角色，将使用默认角色集合
            </div>
          )}
          {agents.map((agent, index) => {
            const roleConfig = AGENT_ROLES.find(r => r.value === agent.role);
            return (
              <div
                key={index}
                className="py-2 px-2.5 bg-[#fff] flex flex-col gap-2 rounded-md border border-[#f0f0f0]"
              >
                <div className="flex items-center gap-2">
                  <Tag
                    color={roleConfig?.color || '#1890FF'}
                    className="mb-0"
                  >
                    {roleConfig?.label || agent.role}
                  </Tag>
                  <FlowInput
                    className="flex-1"
                    placeholder="角色名称"
                    value={agent.name}
                    onChange={e =>
                      handleAgentChange(index, 'name', e.target.value)
                    }
                  />
                  {!canvasesDisabled && (
                    <div
                      className="w-[18px] h-[18px] rounded-full bg-[#F7F7F7] flex items-center justify-center cursor-pointer"
                      onClick={e => {
                        e.stopPropagation();
                        handleRemoveAgent(index);
                      }}
                    >
                      <img
                        src={Icons.agent.knowledgeListDelete}
                        className="w-1.5 h-1.5"
                        alt=""
                      />
                    </div>
                  )}
                </div>
                <FlowSelect
                  value={agent.role}
                  onChange={value =>
                    handleAgentChange(index, 'role', value)
                  }
                >
                  {AGENT_ROLES.map(role => (
                    <FlowSelect.Option key={role.value} value={role.value}>
                      <div className="flex items-center gap-1">
                        <div
                          className="w-2 h-2 rounded-full"
                          style={{ backgroundColor: role.color }}
                        />
                        <div className="text-xs">{role.label}</div>
                      </div>
                    </FlowSelect.Option>
                  ))}
                </FlowSelect>
                <FlowInput
                  placeholder="系统提示词（可选）"
                  value={agent.systemPrompt}
                  onChange={e =>
                    handleAgentChange(index, 'systemPrompt', e.target.value)
                  }
                />
              </div>
            );
          })}
        </div>
      }
    />
  );
};

const MaxIterationsSection = ({
  data,
  handleChangeNodeParam,
}): React.ReactElement => {
  return (
    <div className="bg-[#f8faff] px-[18px] py-2.5 rounded-md flex items-center justify-between">
      <div className="flex items-center gap-1">
        <div className="text-base font-medium">最大迭代次数</div>
        <Tooltip
          title="Supervisor模式下的最大任务分发轮次，防止无限循环"
          getPopupContainer={triggerNode =>
            triggerNode?.parentNode as HTMLElement
          }
        >
          <img
            src={Icons.agent.questionMark}
            className="w-[14px] h-[14px]"
            alt=""
          />
        </Tooltip>
      </div>
      <div className="flex items-center gap-2">
        <div
          className="w-[15px] h-[15px] flex justify-center items-center"
          onClick={() =>
            handleChangeNodeParam(
              (data: MultiAgentNodeData, value: number) =>
                (data.nodeParam.maxIterations = value),
              (data.nodeParam?.maxIterations || 10) - 1 > 0
                ? (data.nodeParam?.maxIterations || 10) - 1
                : 1
            )
          }
        >
          <img
            src={Icons.agent.zoomOutIcon}
            className="w-[15px] h-[2px] cursor-pointer"
            alt=""
          />
        </div>
        <FlowInputNumber
          value={data?.nodeParam?.maxIterations || 10}
          onChange={value =>
            handleChangeNodeParam(
              (data: MultiAgentNodeData, value: number) =>
                (data.nodeParam.maxIterations = value),
              value
            )
          }
          onBlur={() => {
            if (data?.nodeParam?.maxIterations === null) {
              handleChangeNodeParam(
                (data: MultiAgentNodeData, value: number) =>
                  (data.nodeParam.maxIterations = value),
                10
              );
            }
          }}
          min={1}
          max={50}
          precision={0}
          className="nodrag w-[50px]"
          controls={false}
        />
        <div
          className="w-[15px] h-[15px]"
          onClick={() =>
            handleChangeNodeParam(
              (data: MultiAgentNodeData, value: number) =>
                (data.nodeParam.maxIterations = value),
              (data.nodeParam?.maxIterations || 10) + 1 <= 50
                ? (data.nodeParam?.maxIterations || 10) + 1
                : 50
            )
          }
        >
          <img
            src={Icons.agent.zoomInIcon}
            className="w-[15px] h-[16px] cursor-pointer"
            alt=""
          />
        </div>
      </div>
    </div>
  );
};

import useFlowsManager from '@/components/workflow/store/use-flows-manager';

export const MultiAgentDetail = memo((props: MultiAgentDetailProps) => {
  const { id, data } = props;
  const { handleChangeNodeParam } = useNodeCommon({
    id,
    data: data as unknown,
  });
  const { t } = useTranslation();

  return (
    <div>
      <div className="p-[14px] pb-[6px]">
        <div className="bg-[#fff] rounded-lg w-full flex flex-col gap-2.5">
          <Inputs id={id} data={data} />
          <CollaborationModeSection
            data={data}
            handleChangeNodeParam={handleChangeNodeParam}
          />
          <QuerySection
            id={id}
            data={data}
            handleChangeNodeParam={handleChangeNodeParam}
          />
          <AgentListSection
            data={data}
            handleChangeNodeParam={handleChangeNodeParam}
          />
          <MaxIterationsSection
            data={data}
            handleChangeNodeParam={handleChangeNodeParam}
          />
          <Outputs id={id} data={data}>
            <div className="flex-1 flex items-center justify-between">
              <div className="text-base font-medium">输出</div>
            </div>
          </Outputs>
          <ExceptionHandling id={id} data={data} />
        </div>
      </div>
    </div>
  );
});
